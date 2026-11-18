package com.vista.pdg.quiz.dto;

import com.vista.pdg.quiz.entity.VistaSetup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Contratos HTTP de los quizzes de estudio (HU-35). Nunca se exponen entidades. */
public final class StudyQuizDtos {
  private StudyQuizDtos() {}

  public record CreateStudyQuizRequest(
      @Size(max = 160, message = "El título admite hasta 160 caracteres") String title,
      @NotEmpty(message = "Selecciona al menos un tema") List<String> topics,
      @NotNull(message = "Indica el número de preguntas")
          @Min(value = 1, message = "Debe haber al menos 1 pregunta")
          @Max(value = 10, message = "Máximo 10 preguntas")
          Integer questionCount) {}

  /**
   * {@code generated} puede ser menor que {@code questionCount} si la generación se interrumpió.
   */
  public record StudyQuizSummaryDto(
      Long id,
      String title,
      List<String> topics,
      int questionCount,
      int generated,
      int answered,
      int correct,
      int hintsUsed,
      int solutionsShown,
      Instant createdAt) {}

  /**
   * La pista solo viaja si el estudiante la pidió; la opción correcta y la justificación, solo si
   * la pregunta está resuelta (respondida o con la solución a la vista).
   */
  public record StudyQuestionDto(
      int position,
      String topicLabel,
      String scenario,
      String statement,
      List<String> options,
      VistaSetup setup,
      Integer selectedOption,
      boolean hintShown,
      String hint,
      boolean solutionShown,
      Integer correctOption,
      String explanation) {}

  public record StudyQuizDto(StudyQuizSummaryDto quiz, List<StudyQuestionDto> questions) {}

  public record StudyAnswerRequest(
      @NotNull(message = "Elige una opción")
          @Min(value = 0, message = "Opción inválida")
          @Max(value = 3, message = "Opción inválida")
          Integer selectedOption,
      @Min(value = 0, message = "Tiempo inválido") Long elapsedMs) {
    public StudyAnswerRequest(Integer selectedOption) {
      this(selectedOption, null);
    }
  }

  /** Turno previo de la conversación con el tutor; la conversación vive en el navegador. */
  public record TutorTurn(
      @NotBlank String role,
      @NotBlank @Size(max = 2000, message = "Turno demasiado largo") String text) {}

  public record TutorRequest(
      @NotBlank(message = "Escribe tu pregunta")
          @Size(max = 800, message = "La pregunta admite hasta 800 caracteres")
          String message,
      @Size(max = 12, message = "Conversación demasiado larga") List<@Valid TutorTurn> history) {}

  public record TutorReplyDto(String reply, int remaining) {}
}
