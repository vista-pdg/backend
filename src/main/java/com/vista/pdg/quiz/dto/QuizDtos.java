package com.vista.pdg.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Contratos HTTP de quizzes (HU-34). Nunca se exponen entidades. */
public final class QuizDtos {
  private QuizDtos() {}

  // ── Gestión (docente / administrador) ─────────────────────────────────

  public record ManagedCourseDto(String code, String name, String termCode, int enrolledStudents) {}

  public record TopicDto(
      String key, String label, String family, String familyLabel, String description) {}

  public record CreateQuizRequest(
      @NotBlank(message = "Selecciona un curso") String courseCode,
      @NotBlank(message = "Escribe un título")
          @Size(max = 160, message = "El título admite hasta 160 caracteres")
          String title,
      @NotEmpty(message = "Selecciona al menos un tema") List<String> topics,
      @NotNull(message = "Indica las preguntas por estudiante")
          @Min(value = 1, message = "Debe haber al menos 1 pregunta por estudiante")
          @Max(value = 20, message = "Máximo 20 preguntas por estudiante")
          Integer questionsPerStudent,
      @Min(value = 1, message = "Debe haber al menos 1 estudiante")
          @Max(value = 300, message = "Máximo 300 estudiantes")
          Integer studentCount,
      @NotNull(message = "Indica el tiempo límite")
          @Min(value = 1, message = "El tiempo límite mínimo es 1 minuto")
          @Max(value = 240, message = "El tiempo límite máximo es 240 minutos")
          Integer timeLimitMinutes,
      @NotNull(message = "Indica la fecha de apertura") Instant opensAt,
      @NotNull(message = "Indica la fecha de cierre") Instant closesAt,
      boolean showGrade) {}

  /** {@code phase}: DRAFT, SCHEDULED, OPEN o CLOSED según el estado y la ventana. */
  public record QuizSummaryDto(
      Long id,
      String courseCode,
      String title,
      String status,
      String phase,
      List<TopicDto> topics,
      int questionsPerStudent,
      int expectedStudents,
      int bankSize,
      int bankTarget,
      int timeLimitMinutes,
      Instant opensAt,
      Instant closesAt,
      boolean showGrade,
      int enrolledStudents,
      int attemptsStarted,
      int attemptsFinished) {}

  public record BankQuestionDto(
      Long id,
      String topicKey,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      int correctIndex,
      String explanation,
      int timesAssigned) {}

  public record QuizDetailDto(QuizSummaryDto quiz, List<BankQuestionDto> questions) {}

  public record GenerateBatchResponse(
      int generated, int discarded, int bankSize, int bankTarget, boolean complete) {}

  public record GradeVisibilityRequest(
      @NotNull(message = "Indica la visibilidad") Boolean showGrade) {}

  public record ResultStats(
      Double average,
      Double highest,
      Double lowest,
      int finished,
      int inProgress,
      int notStarted,
      int enrolled) {}

  /** {@code answers}: CORRECT, INCORRECT o UNANSWERED por pregunta, en el orden del intento. */
  public record StudentResultRow(
      Long studentId,
      String displayName,
      String email,
      String status,
      Integer score,
      Integer total,
      Double grade,
      Integer integrityEvents,
      Instant startedAt,
      Instant finishedAt,
      List<String> answers) {}

  public record QuizResultsDto(
      QuizSummaryDto quiz, ResultStats stats, List<StudentResultRow> rows) {}

  // ── Estudiante ────────────────────────────────────────────────────────

  /** Los campos de nota son nulos cuando el docente no ha activado su visualización. */
  public record AttemptResultDto(
      String status,
      Instant finishedAt,
      boolean gradeVisible,
      Integer score,
      Integer total,
      Double grade) {}

  /** {@code state}: UPCOMING, OPEN, IN_PROGRESS, FINISHED o MISSED. */
  public record StudentQuizDto(
      Long id,
      String title,
      List<String> topics,
      int questionCount,
      int timeLimitMinutes,
      Instant opensAt,
      Instant closesAt,
      String state,
      Instant deadline,
      AttemptResultDto result) {}

  /** {@code courseCode} nulo: la cuenta no está matriculada y no puede presentar quizzes. */
  public record StudentQuizListDto(
      String courseCode, String courseName, List<StudentQuizDto> quizzes) {}

  /** Nunca incluye la opción correcta ni la justificación. */
  public record AttemptQuestionDto(
      int position,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      Integer selectedOption) {}

  public record AttemptDto(
      Long quizId,
      String title,
      String status,
      Instant startedAt,
      Instant deadline,
      Instant serverTime,
      int integrityEvents,
      List<AttemptQuestionDto> questions,
      AttemptResultDto result) {}

  public record AnswerRequest(
      @Min(value = 0, message = "Opción inválida") @Max(value = 3, message = "Opción inválida")
          Integer selectedOption) {}

  public record IntegrityEventRequest(
      @NotBlank(message = "Indica el tipo de incidente")
          @Size(max = 40, message = "Tipo de incidente demasiado largo")
          String type) {}

  public record IntegrityEventResponse(int integrityEvents) {}
}
