package com.vista.pdg.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Contenido de una pregunta generada (HU-34 y HU-35): el banco de un quiz de curso y un quiz de
 * estudio guardan exactamente lo mismo, así que lo comparten. Las columnas conservan los nombres
 * que ya tenía {@code quiz_questions}.
 */
@Embeddable
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuestionContent {
  @Column(name = "topic_key", nullable = false, length = 96)
  private String topicKey;

  @Column(name = "topic_label", nullable = false, length = 160)
  private String topicLabel;

  @Column(nullable = false, columnDefinition = "text")
  private String scenario;

  @Column(nullable = false, columnDefinition = "text")
  private String statement;

  @Convert(converter = StringListJsonConverter.class)
  @Column(nullable = false, columnDefinition = "text")
  private List<String> options;

  @Column(name = "correct_index", nullable = false)
  private int correctIndex;

  @Column(columnDefinition = "text")
  private String explanation;

  /** Pista sin la respuesta; nula en preguntas anteriores a HU-35. */
  @Column(columnDefinition = "text")
  private String hint;

  /** Rastro verificado del algoritmo sobre el escenario; lo usa el tutor de estudio. */
  @Convert(converter = StringListJsonConverter.class)
  @Column(columnDefinition = "text")
  private List<String> facts;

  @Convert(converter = VistaSetupJsonConverter.class)
  @Column(name = "vista_setup", columnDefinition = "text")
  private VistaSetup setup;

  /** Las mismas opciones en otro orden, con la correcta reubicada. */
  public QuestionContent shuffled(Random random) {
    List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
    java.util.Collections.shuffle(order, random);
    return toBuilder()
        .options(order.stream().map(options::get).toList())
        .correctIndex(order.indexOf(correctIndex))
        .build();
  }
}
