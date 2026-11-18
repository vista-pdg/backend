package com.vista.pdg.quiz.service;

import com.vista.pdg.quiz.dto.QuizDtos.QuestionEditRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HU-36 · CA-3: reglas de forma de una pregunta editada a mano. Son las del redactor salvo las
 * heurísticas de calidad (largo relativo de la correcta), que en la interfaz solo advierten.
 */
final class QuestionRules {
  static final int MIN_STATEMENT = 20;
  static final int MAX_STATEMENT = 1200;
  static final int MAX_OPTION = 320;
  static final int MAX_EXPLANATION = 2000;
  static final int MAX_HINT = 200;

  record Edited(
      String statement, List<String> options, int correctIndex, String explanation, String hint) {}

  private QuestionRules() {}

  static Edited validate(QuestionEditRequest request) {
    String statement = strip(request.statement());
    if (statement.length() < MIN_STATEMENT) {
      throw QuizException.invalid(
          "statement", "El enunciado necesita al menos " + MIN_STATEMENT + " caracteres");
    }
    if (statement.length() > MAX_STATEMENT) {
      throw QuizException.invalid(
          "statement", "El enunciado admite hasta " + MAX_STATEMENT + " caracteres");
    }
    if (request.options() == null || request.options().size() != QuizQuestionWriter.OPTIONS) {
      throw QuizException.invalid("options", "Escribe exactamente cuatro opciones");
    }
    List<String> options = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < request.options().size(); i++) {
      String option = strip(request.options().get(i));
      String field = "options." + i;
      if (option.isEmpty()) throw QuizException.invalid(field, "Escribe la opción " + (i + 1));
      if (option.length() > MAX_OPTION) {
        throw QuizException.invalid(field, "La opción admite hasta " + MAX_OPTION + " caracteres");
      }
      String normalized = QuizQuestionWriter.normalize(option);
      if (QuizQuestionWriter.isWildcard(normalized)) {
        throw QuizException.invalid(
            field,
            "Las opciones se barajan para cada estudiante: evita «todas» o «ninguna de las"
                + " anteriores»");
      }
      if (!seen.add(normalized)) {
        throw QuizException.invalid(
            field, "Esta opción repite otra; las cuatro deben ser distintas");
      }
      options.add(option);
    }
    Integer correct = request.correctIndex();
    if (correct == null || correct < 0 || correct >= QuizQuestionWriter.OPTIONS) {
      throw QuizException.invalid("correctIndex", "Marca la opción correcta");
    }
    String explanation = strip(request.explanation());
    if (explanation.length() > MAX_EXPLANATION) {
      throw QuizException.invalid(
          "explanation", "La justificación admite hasta " + MAX_EXPLANATION + " caracteres");
    }
    String hint = strip(request.hint());
    if (hint.length() > MAX_HINT) {
      throw QuizException.invalid("hint", "La pista admite hasta " + MAX_HINT + " caracteres");
    }
    return new Edited(
        statement,
        List.copyOf(options),
        correct,
        explanation.isEmpty() ? null : explanation,
        hint.isEmpty() ? null : hint);
  }

  private static String strip(String text) {
    return text == null ? "" : text.strip();
  }
}
