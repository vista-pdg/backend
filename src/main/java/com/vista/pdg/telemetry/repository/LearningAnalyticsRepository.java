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

/**
 * HU-36: respuestas de quizzes y de quizzes de estudio de un curso, para agregarlas por tema y por
 * pregunta. Las identidades solo se usan dentro del servicio para el umbral y el cruce con la
 * práctica; nunca llegan al reporte.
 */
@Repository
@RequiredArgsConstructor
public class LearningAnalyticsRepository {
  private final NamedParameterJdbcTemplate jdbc;

  /** Una pregunta asignada en un intento finalizado. {@code selected} es el índice original. */
  public record QuizItemRow(
      long quizId,
      long attemptId,
      long studentId,
      Instant startedAt,
      long questionId,
      String topicKey,
      String topicLabel,
      String statement,
      String optionsJson,
      int correctIndex,
      Integer selected,
      Integer timeSpentMs) {}

  public record AttemptRow(long quizId, long studentId, Integer score, int total) {}

  public record QuizRow(
      long id,
      String title,
      String status,
      Instant opensAt,
      Instant closesAt,
      int questionsPerStudent) {}

  public record StudyRow(
      long ownerId,
      String topicKey,
      String topicLabel,
      Integer selected,
      int correctIndex,
      boolean hintShown,
      boolean solutionShown,
      Integer timeSpentMs) {}

  public List<QuizItemRow> quizItems(long courseId, AnalyticsFilter filter) {
    String sql =
        """
        select z.id as quiz_id, a.id as attempt_id, a.student_id, a.started_at, q.id as question_id,
          q.topic_key, q.topic_label, q.statement, q.options, q.correct_index,
          i.selected_option, i.time_spent_ms
        from quiz_attempt_items i
        join quiz_attempts a on a.id = i.attempt_id
        join quizzes z on z.id = a.quiz_id
        join quiz_questions q on q.id = i.question_id
        where z.course_id = :courseId and a.status <> 'IN_PROGRESS'
        """
            + range("a.started_at", filter)
            + (filter.quizId() == null ? "" : " and z.id = :quizId")
            + " order by a.id, i.position";
    return jdbc.query(
        sql,
        params(courseId, filter),
        (rs, n) ->
            new QuizItemRow(
                rs.getLong("quiz_id"),
                rs.getLong("attempt_id"),
                rs.getLong("student_id"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getLong("question_id"),
                rs.getString("topic_key"),
                rs.getString("topic_label"),
                rs.getString("statement"),
                rs.getString("options"),
                rs.getInt("correct_index"),
                (Integer) rs.getObject("selected_option"),
                (Integer) rs.getObject("time_spent_ms")));
  }

  public List<AttemptRow> finishedAttempts(long courseId, AnalyticsFilter filter) {
    String sql =
        """
        select a.quiz_id, a.student_id, a.score, a.total
        from quiz_attempts a join quizzes z on z.id = a.quiz_id
        where z.course_id = :courseId and a.status <> 'IN_PROGRESS'
        """
            + range("a.started_at", filter)
            + (filter.quizId() == null ? "" : " and z.id = :quizId");
    return jdbc.query(
        sql,
        params(courseId, filter),
        (rs, n) ->
            new AttemptRow(
                rs.getLong("quiz_id"),
                rs.getLong("student_id"),
                (Integer) rs.getObject("score"),
                rs.getInt("total")));
  }

  /** Quizzes publicados del curso; con filtro de quiz, solo ese. */
  public List<QuizRow> publishedQuizzes(long courseId, Long quizId) {
    return jdbc.query(
        """
        select id, title, status, opens_at, closes_at, questions_per_student from quizzes
        where course_id = :courseId and status = 'PUBLISHED'
        """
            + (quizId == null ? "" : " and id = :quizId")
            + " order by opens_at, id",
        quizId == null
            ? Map.of("courseId", courseId)
            : Map.of("courseId", courseId, "quizId", quizId),
        (rs, n) ->
            new QuizRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getTimestamp("opens_at").toInstant(),
                rs.getTimestamp("closes_at").toInstant(),
                rs.getInt("questions_per_student")));
  }

  /** Preguntas de estudio resueltas (respondidas o con la solución vista) por alumnos del curso. */
  public List<StudyRow> study(long courseId, AnalyticsFilter filter) {
    String sql =
        """
        select s.owner_id, q.topic_key, q.topic_label, q.selected_option, q.correct_index,
          q.hint_shown, q.solution_shown, q.time_spent_ms
        from study_questions q
        join study_quizzes s on s.id = q.study_quiz_id
        join users u on u.id = s.owner_id
        where u.course_id = :courseId
          and (q.selected_option is not null or q.solution_shown is true)
        """
            + range("coalesce(q.answered_at, q.created_at)", filter);
    return jdbc.query(
        sql,
        params(courseId, filter),
        (rs, n) ->
            new StudyRow(
                rs.getLong("owner_id"),
                rs.getString("topic_key"),
                rs.getString("topic_label"),
                (Integer) rs.getObject("selected_option"),
                rs.getInt("correct_index"),
                rs.getBoolean("hint_shown"),
                rs.getBoolean("solution_shown"),
                (Integer) rs.getObject("time_spent_ms")));
  }

  /** Estudiantes con un intento o una respuesta de estudio en el periodo, sin filtro de quiz. */
  public Set<Long> activeStudents(long courseId, AnalyticsFilter filter) {
    AnalyticsFilter period =
        new AnalyticsFilter(filter.courseCode(), filter.from(), filter.to(), null);
    Set<Long> ids =
        new HashSet<>(
            jdbc.queryForList(
                """
                select distinct a.student_id from quiz_attempts a
                join quizzes z on z.id = a.quiz_id where z.course_id = :courseId
                """
                    + range("a.started_at", period),
                params(courseId, period),
                Long.class));
    ids.addAll(
        jdbc.queryForList(
            """
            select distinct s.owner_id from study_questions q
            join study_quizzes s on s.id = q.study_quiz_id
            join users u on u.id = s.owner_id
            where u.course_id = :courseId
              and (q.selected_option is not null or q.solution_shown is true)
            """
                + range("coalesce(q.answered_at, q.created_at)", period),
            params(courseId, period),
            Long.class));
    return ids;
  }

  private static String range(String column, AnalyticsFilter filter) {
    return (filter.start() == null ? "" : " and " + column + " >= :start")
        + (filter.endExclusive() == null ? "" : " and " + column + " < :end");
  }

  private static Map<String, Object> params(long courseId, AnalyticsFilter filter) {
    Map<String, Object> params = new HashMap<>();
    params.put("courseId", courseId);
    if (filter.quizId() != null) params.put("quizId", filter.quizId());
    if (filter.start() != null) params.put("start", Timestamp.from(filter.start()));
    if (filter.endExclusive() != null) params.put("end", Timestamp.from(filter.endExclusive()));
    return params;
  }
}
