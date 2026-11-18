package com.vista.pdg.quiz.service;

import com.vista.pdg.quiz.dto.QuizDtos.TopicDto;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Temas de quiz: son las entradas del catálogo de algoritmos de VISTA, así que un algoritmo nuevo
 * queda disponible para evaluar sin tocar este módulo, siempre que VISTA pueda reproducirlo.
 */
@Component
@RequiredArgsConstructor
public class QuizTopics {
  private final AlgorithmDispatcher dispatcher;
  private final QuizScenarioFactory scenarios;

  /** Temas que se pueden evaluar: los del catálogo que VISTA reproduce (HU-35 · CA-2). */
  public List<TopicDto> all() {
    return dispatcher.catalog().stream()
        .filter(scenarios::supports)
        .map(QuizTopics::toDto)
        .toList();
  }

  /** Cualquier entrada del catálogo, incluso las que ya no se ofrecen; sirve para leer quizzes. */
  public Optional<AlgorithmDescriptor> find(String key) {
    return dispatcher.catalog().stream().filter(d -> d.key().equals(key)).findFirst();
  }

  /** Un tema con el que todavía se pueden generar preguntas. */
  public Optional<AlgorithmDescriptor> available(String key) {
    return find(key).filter(scenarios::supports);
  }

  public static TopicDto toDto(AlgorithmDescriptor d) {
    return new TopicDto(d.key(), label(d), d.family(), familyLabel(d.family()), d.description());
  }

  /** El catálogo repite etiquetas entre subtipos («Búsqueda»); la estructura las desambigua. */
  public static String label(AlgorithmDescriptor d) {
    return structureName(d) + " — " + d.label();
  }

  public static String structureName(AlgorithmDescriptor d) {
    if ("sorting".equals(d.family())) return "Ordenamiento";
    return switch (d.type() + "/" + d.subtype()) {
      case "tree/bst" -> "Árbol BST";
      case "tree/avl" -> "Árbol AVL";
      case "tree/btree" -> "B-árbol";
      case "tree/heap" -> "Heap máximo";
      case "graph/simple" -> "Grafo";
      case "stack/simple" -> "Pila";
      case "queue/simple" -> "Cola";
      case "linked-list/simple" -> "Lista enlazada";
      case "hash-table/chaining" -> "Tabla hash";
      default -> d.type();
    };
  }

  public static String familyLabel(String family) {
    return switch (family) {
      case "tree" -> "Árboles";
      case "heap" -> "Heaps";
      case "graph" -> "Grafos";
      case "stack" -> "Pilas";
      case "queue" -> "Colas";
      case "linked-list" -> "Listas enlazadas";
      case "sorting" -> "Ordenamiento";
      case "hash-table" -> "Tablas hash";
      default -> family;
    };
  }
}
