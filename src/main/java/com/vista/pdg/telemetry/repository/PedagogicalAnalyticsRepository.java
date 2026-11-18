package com.vista.pdg.telemetry.repository;

import com.vista.pdg.telemetry.service.AnalyticsFilter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  public record PracticeCount(
      String structureType, String subtype, String algorithm, long executions) {}

  public record FirstPractice(
      String pseudonym, String structureType, String subtype, String algorithm, Instant first) {}

  // V1 carecía de detalle para grafos. No inferir dirigido/no dirigido sin evidencia.
  private static final String SCOPED_HEAD =
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
          and pseudonym is not null and pseudonym <> ''
      """;

  /** HU-36: las fechas son opcionales; sin ellas cuenta todo el periodo del curso. */
  private static String scoped(AnalyticsFilter filter) {
    return SCOPED_HEAD
        + (filter.start() == null ? "" : " and created_at >= :start")
        + (filter.endExclusive() == null ? "" : " and created_at < :end")
        + "\n)\n";
  }

  public List<StructureCount> structures(AnalyticsFilter filter, String term) {
    return jdbc.query(
        scoped(filter)
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
        scoped(filter)
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
        scoped(filter)
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

  /** HU-36: seudónimos con actividad en el alcance; no salen del servidor, solo su conteo. */
  public Set<String> activePseudonyms(AnalyticsFilter filter, String term) {
    return new HashSet<>(
        jdbc.queryForList(
            scoped(filter) + "select distinct pseudonym from scoped",
            params(filter, term),
            String.class));
  }

  /** HU-36: ejecuciones de algoritmos del alcance, para relacionarlas con los temas. */
  public List<PracticeCount> practice(AnalyticsFilter filter, String term) {
    return jdbc.query(
        scoped(filter)
            + """
        select lower(structure_type) as structure_type, lower(coalesce(subtype, '')) as subtype,
          lower(algorithm) as algorithm, count(*) as executions
        from scoped where kind = 'algorithm' and algorithm is not null
        group by 1, 2, 3
        """,
        params(filter, term),
        (rs, n) ->
            new PracticeCount(
                rs.getString("structure_type"),
                rs.getString("subtype"),
                rs.getString("algorithm"),
                rs.getLong("executions")));
  }

  /** Primera ejecución de cada algoritmo por estudiante en el periodo, sin el filtro de fechas. */
  public List<FirstPractice> firstPractice(String course, String term) {
    return jdbc.query(
        scoped(new AnalyticsFilter(course, null, null))
            + """
        select pseudonym, lower(structure_type) as structure_type,
          lower(coalesce(subtype, '')) as subtype, lower(algorithm) as algorithm,
          min(created_at) as first
        from scoped where kind = 'algorithm' and algorithm is not null
        group by 1, 2, 3, 4
        """,
        params(new AnalyticsFilter(course, null, null), term),
        (rs, n) ->
            new FirstPractice(
                rs.getString("pseudonym"),
                rs.getString("structure_type"),
                rs.getString("subtype"),
                rs.getString("algorithm"),
                rs.getTimestamp("first").toInstant()));
  }

  private static Map<String, Object> params(AnalyticsFilter filter, String term) {
    Map<String, Object> params = new HashMap<>();
    params.put("course", filter.courseCode());
    params.put("term", term);
    if (filter.start() != null) params.put("start", Timestamp.from(filter.start()));
    if (filter.endExclusive() != null) params.put("end", Timestamp.from(filter.endExclusive()));
    return params;
  }
}
