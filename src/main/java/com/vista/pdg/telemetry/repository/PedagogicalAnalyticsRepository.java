package com.vista.pdg.telemetry.repository;

import com.vista.pdg.telemetry.service.AnalyticsFilter;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Agrega en Postgres; las identidades utilizadas para el umbral nunca salen del repositorio. */
@Repository
@RequiredArgsConstructor
public class PedagogicalAnalyticsRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public record StructureCount(String structure, long frequency, long participants) {}

  public record AlgorithmCount(
      String algorithm,
      long executions,
      long participants,
      long measuredExecutions,
      long measuredParticipants,
      Double averageSteps) {}

  public record RetryCount(
      String structure,
      long sessions,
      long retries,
      long participants,
      long retryParticipants,
      long otherParticipants) {}

  // V1 carecía de detalle para grafos. No inferir dirigido/no dirigido sin evidencia.
  private static final String SCOPED =
      """
      with scoped as (
        select e.*, case lower(structure_type)
          when 'graph' then 'grafo_sin_detalle'
          when 'tree' then case lower(coalesce(subtype, ''))
            when 'avl' then 'arbol_avl' when 'bst' then 'arbol_bst'
            when 'heap' then 'heap' else 'arbol_binario' end
          when 'linked-list' then case lower(coalesce(subtype, ''))
            when 'doubly' then 'lista_doble' when 'circular' then 'lista_circular'
            else 'lista_simple' end
          when 'stack' then 'pila' when 'queue' then 'cola'
          when 'hash-table' then 'tabla_hash' else lower(structure_type)
        end as structure
        from generation_events e
        where course_code = :course and term_code = :term
          and student_participant is true
          and created_at >= :start and created_at < :end
          and pseudonym is not null and pseudonym <> ''
      )
      """;

  public List<StructureCount> structures(AnalyticsFilter filter, String term) {
    return jdbc.query(
        SCOPED
            + """
        select structure, count(*) as frequency, count(distinct pseudonym) as participants
        from scoped where coalesce(kind, 'generation') = 'generation'
        group by structure
        """,
        params(filter, term),
        (rs, n) ->
            new StructureCount(
                rs.getString("structure"), rs.getLong("frequency"), rs.getLong("participants")));
  }

  public List<AlgorithmCount> algorithms(AnalyticsFilter filter, String term) {
    return jdbc.query(
        SCOPED
            + """
        select upper(algorithm) as algorithm, count(*) as executions,
          count(distinct pseudonym) as participants,
          count(completed_steps) as measured,
          count(distinct pseudonym) filter (where completed_steps is not null) as measured_participants,
          avg(completed_steps) as average_steps
        from scoped where kind = 'algorithm' and algorithm is not null
        group by upper(algorithm)
        """,
        params(filter, term),
        (rs, n) ->
            new AlgorithmCount(
                rs.getString("algorithm"),
                rs.getLong("executions"),
                rs.getLong("participants"),
                rs.getLong("measured"),
                rs.getLong("measured_participants"),
                rs.getBigDecimal("average_steps") == null
                    ? null
                    : rs.getBigDecimal("average_steps").doubleValue()));
  }

  /** Tres solicitudes dentro de cualquier ventana de 120 segundos de la sesión. */
  public List<RetryCount> retries(AnalyticsFilter filter, String term) {
    return jdbc.query(
        SCOPED
            + """
        , attempts as (
          select structure, pseudonym, session_id, created_at,
            lead(created_at, 2) over (
              partition by structure, pseudonym, session_id order by created_at, id
            ) as third_at
          from scoped where coalesce(kind, 'generation') = 'generation'
            and session_id is not null and session_id <> ''
        ), sessions as (
          select structure, pseudonym, session_id,
            bool_or(coalesce(third_at <= created_at + interval '120 seconds', false)) as retried
          from attempts group by structure, pseudonym, session_id
        )
        select structure, count(*) as sessions,
          count(*) filter (where retried) as retries,
          count(distinct pseudonym) as participants,
          count(distinct pseudonym) filter (where retried) as retry_participants,
          count(distinct pseudonym) filter (where not retried) as other_participants
        from sessions group by structure
        """,
        params(filter, term),
        (rs, n) ->
            new RetryCount(
                rs.getString("structure"),
                rs.getLong("sessions"),
                rs.getLong("retries"),
                rs.getLong("participants"),
                rs.getLong("retry_participants"),
                rs.getLong("other_participants")));
  }

  private static Map<String, Object> params(AnalyticsFilter filter, String term) {
    return Map.of(
        "course",
        filter.courseCode(),
        "term",
        term,
        "start",
        Timestamp.from(filter.start()),
        "end",
        Timestamp.from(filter.endExclusive()));
  }
}
