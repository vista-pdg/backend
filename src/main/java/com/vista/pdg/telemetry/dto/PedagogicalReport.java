package com.vista.pdg.telemetry.dto;

import com.vista.pdg.academic.dto.CourseDto;
import java.time.LocalDate;
import java.util.List;

/**
 * Contrato público: nunca contiene identidades, sesiones ni filtros por persona. HU-36: el umbral
 * de anonimato es del grupo ({@code status}); si no se alcanza, todas las listas van vacías y
 * {@code activeStudents} es nulo. Las tasas son fracciones entre 0 y 1.
 */
public record PedagogicalReport(
    CourseDto course,
    LocalDate from,
    LocalDate to,
    boolean wholeTerm,
    QuizRef quiz,
    String timezone,
    int minimumParticipants,
    Status status,
    Integer activeStudents,
    Integer enrolledStudents,
    double retryThreshold,
    int retryMinAttempts,
    int retryWindowSeconds,
    Long totalRequests,
    Overview overview,
    List<TopicInsight> topics,
    List<QuestionInsight> hardestQuestions,
    List<QuestionInsight> easiestQuestions,
    List<QuizInsight> quizzes,
    List<StructureMetric> structures,
    List<AlgorithmMetric> algorithms,
    List<RetryMetric> retries) {

  public enum Status {
    AVAILABLE,
    SUPPRESSED,
    UNAVAILABLE
  }

  /** Nivel de un tema: {@code DIFICIL} < 50 %, {@code EN_PROGRESO} < 75 %, {@code DOMINADO}. */
  public enum Level {
    DIFICIL,
    EN_PROGRESO,
    DOMINADO,
    SIN_DATOS
  }

  public record QuizRef(Long id, String title) {}

  public record Overview(
      long structureRequests,
      long algorithmExecutions,
      long quizAnswers,
      Double quizAccuracy,
      Double unansweredRate,
      int quizzesWithAnswers,
      long finishedAttempts,
      long studyAnswers,
      Double studyAccuracy,
      Double medianSeconds,
      Double timedShare) {}

  public record TopicInsight(
      String topicKey,
      String topicLabel,
      Level level,
      boolean smallSample,
      long quizAnswers,
      Double quizAccuracy,
      Double unansweredRate,
      Double medianSeconds,
      long studyAnswers,
      Double studyAccuracy,
      Double hintRate,
      Double solutionRate,
      long practiceExecutions,
      Double practicedAccuracy,
      Double unpracticedAccuracy,
      String commonMistake,
      Double commonMistakeShare) {}

  public record QuestionInsight(
      Long quizId,
      String quizTitle,
      String topicKey,
      String topicLabel,
      String statement,
      long answers,
      double accuracy,
      double unansweredRate,
      Double medianSeconds,
      String correctOption,
      String commonWrongOption,
      Double commonWrongShare) {}

  public record QuizInsight(
      Long id,
      String title,
      String phase,
      long finished,
      int enrolled,
      Double averageGrade,
      Double accuracy,
      Double medianSeconds,
      String weakestTopic,
      String strongestTopic) {}

  public record StructureMetric(
      String structure, Status status, Long frequency, Double percentage) {}

  public record AlgorithmMetric(
      String algorithm,
      Status status,
      Long executions,
      Status stepsStatus,
      Double averageCompletedSteps) {}

  public record RetryMetric(
      String structure,
      Status status,
      Long sessions,
      Long sessionsWithRetries,
      Double retryRate,
      Boolean highlighted) {}
}
