package com.vista.pdg.quiz.service;

import com.vista.pdg.quiz.material.MaterialExcerpts.Excerpt;
import java.util.List;

/**
 * HU-36: lo que el docente aporta a la redacción de un lote: sus instrucciones y los fragmentos del
 * material relacionados con el tema. Los quizzes de estudio usan {@link #NONE}.
 */
public record QuizGuidance(String instructions, List<Excerpt> excerpts) {
  public static final QuizGuidance NONE = new QuizGuidance(null, List.of());

  public QuizGuidance {
    instructions = instructions == null || instructions.isBlank() ? null : instructions.strip();
    excerpts = excerpts == null ? List.of() : List.copyOf(excerpts);
  }

  /** Texto del docente sin espacios sobrantes; nulo si está vacío. */
  public static String clean(String instructions) {
    return instructions == null || instructions.isBlank() ? null : instructions.strip();
  }

  public boolean isEmpty() {
    return instructions == null && excerpts.isEmpty();
  }
}
