package com.vista.pdg.quiz.dto;

import com.vista.pdg.quiz.entity.VistaSetup;
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
      boolean showGrade,
      @Size(max = 2000, message = "Las instrucciones admiten hasta 2000 caracteres")
          String instructions) {
    /** Compatibilidad: un quiz sin instrucciones para la IA (HU-34). */
    public CreateQuizRequest(
        String courseCode,
        String title,
        List<String> topics,
        Integer questionsPerStudent,
        Integer studentCount,
        Integer timeLimitMinutes,
        Instant opensAt,
        Instant closesAt,
        boolean showGrade) {
      this(
          courseCode,
          title,
          topics,
          questionsPerStudent,
          studentCount,
          timeLimitMinutes,
          opensAt,
          closesAt,
          showGrade,
          null);
    }
  }

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

  /**
   * {@code editable} (HU-36): el quiz es borrador o la pregunta nunca se asignó. {@code editKind}:
   * {@code MANUAL}, {@code AI} o nulo.
   */
  public record BankQuestionDto(
      Long id,
      String topicKey,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      int correctIndex,
      String explanation,
      String hint,
      int timesAssigned,
      boolean editable,
      String editKind,
      Instant editedAt) {}

  /** HU-36: una fuente de material ya leída; nunca incluye el texto completo. */
  public record MaterialDto(
      Long id,
      String kind,
      String name,
      String source,
      String format,
      String formatLabel,
      int pages,
      int characters,
      boolean truncated,
      Instant createdAt) {}

  public record QuizDetailDto(
      QuizSummaryDto quiz,
      List<BankQuestionDto> questions,
      String instructions,
      List<MaterialDto> materials) {}

  public record GuidanceRequest(
      @Size(max = 2000, message = "Las instrucciones admiten hasta 2000 caracteres")
          String instructions) {}

  public record GuidanceDto(String instructions, List<MaterialDto> materials) {}

  public record MaterialLinkRequest(
      @NotBlank(message = "Escribe un enlace")
          @Size(max = 2048, message = "El enlace es demasiado largo")
          String url,
      boolean crawl) {}

  /** HU-36 · CA-3: contenido editable de una pregunta; el escenario no se edita. */
  public record QuestionEditRequest(
      @NotNull(message = "Escribe el enunciado") String statement,
      @NotNull(message = "Escribe las cuatro opciones") List<String> options,
      @NotNull(message = "Marca la opción correcta") Integer correctIndex,
      String explanation,
      String hint,
      String source) {}

  public record AiProposalRequest(
      @NotBlank(message = "Escribe qué quieres cambiar")
          @Size(max = 600, message = "Las instrucciones admiten hasta 600 caracteres")
          String instruction) {}

  public record AiProposalDto(
      String statement, List<String> options, int correctIndex, String explanation, String hint) {}

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
      AttemptResultDto result,
      boolean reviewAvailable) {}

  /** {@code courseCode} nulo: la cuenta no está matriculada y no puede presentar quizzes. */
  public record StudentQuizListDto(
      String courseCode, String courseName, List<StudentQuizDto> quizzes) {}

  /**
   * Nunca incluye la opción correcta ni la justificación. {@code setup} (HU-35) dice cómo
   * reproducir el escenario en VISTA; es nulo en preguntas anteriores.
   */
  public record AttemptQuestionDto(
      int position,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      Integer selectedOption,
      VistaSetup setup) {}

  /**
   * Revisión de solo lectura (HU-35 · CA-4). Las opciones van en el orden en que el estudiante las
   * vio; {@code outcome} es CORRECT, INCORRECT o UNANSWERED.
   */
  public record ReviewQuestionDto(
      int position,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      Integer selectedOption,
      int correctOption,
      String outcome,
      String explanation) {}

  public record AttemptReviewDto(
      Long quizId,
      String title,
      String status,
      Instant finishedAt,
      Integer score,
      int total,
      Double grade,
      List<ReviewQuestionDto> questions) {}

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

  /** {@code elapsedMs} (HU-36): tiempo visible de la pregunta; el servidor lo acota al intento. */
  public record AnswerRequest(
      @Min(value = 0, message = "Opción inválida") @Max(value = 3, message = "Opción inválida")
          Integer selectedOption,
      @Min(value = 0, message = "Tiempo inválido") Long elapsedMs) {
    public AnswerRequest(Integer selectedOption) {
      this(selectedOption, null);
    }
  }

  public record IntegrityEventRequest(
      @NotBlank(message = "Indica el tipo de incidente")
          @Size(max = 40, message = "Tipo de incidente demasiado largo")
          String type) {}

  public record IntegrityEventResponse(int integrityEvents) {}
}
