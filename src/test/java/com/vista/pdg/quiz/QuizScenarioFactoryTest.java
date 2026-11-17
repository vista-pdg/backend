package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.quiz.service.QuizScenarioFactory;
import com.vista.pdg.quiz.service.QuizScenarioFactory.Scenario;
import com.vista.pdg.quiz.service.QuizTopics;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HU-34 · CA-2: los hechos que recibe el modelo salen del algoritmo real, sobre todo el catálogo.
 */
class QuizScenarioFactoryTest extends IntegrationTestSupport {
  @Autowired private QuizScenarioFactory factory;
  @Autowired private QuizTopics topics;

  private Scenario scenario(String key, long seed) {
    return factory.build(topics.find(key).orElseThrow(), new Random(seed), 1);
  }

  @Test
  void valueAlgorithmsCarryTheRealTraceOfTheDrawnValues() {
    Scenario avl = scenario("tree/avl/insert", 7);
    assertThat(avl.description()).startsWith("Valores de entrada, en este orden: [");
    assertThat(avl.facts()).isNotEmpty().hasSizeLessThanOrEqualTo(30);
    assertThat(avl.facts()).allMatch(fact -> fact.length() <= 201);
    // Misma semilla, mismo escenario: el sorteo es reproducible.
    assertThat(scenario("tree/avl/insert", 7)).isEqualTo(avl);
    assertThat(scenario("tree/avl/insert", 8).description()).isNotEqualTo(avl.description());
  }

  @Test
  void graphAlgorithmsRunOnAConnectedWeightedGraph() {
    for (String key :
        new String[] {"graph/simple/bfs", "graph/simple/dijkstra", "graph/simple/kruskal"}) {
      Scenario graph = scenario(key, 11);
      assertThat(graph.description())
          .startsWith("Grafo no dirigido y ponderado con vértices A, B, C, D, E y F.")
          .endsWith("Nodo inicial: A.");
      assertThat(graph.facts()).as(key).isNotEmpty();
      // Conexo: ningún rastro reporta vértices inalcanzables.
      assertThat(graph.facts()).noneMatch(fact -> fact.contains("No alcanzables"));
    }
  }

  @Test
  void canvasAlgorithmsAreBuiltFirstSoTheirTraceIsAlsoVerified() {
    Scenario inorder = scenario("tree/bst/inorder", 3);
    assertThat(inorder.description()).startsWith("Árbol BST construido con los valores");
    assertThat(inorder.facts()).as("inorder").isNotEmpty();
    Scenario search = scenario("tree/bst/search", 3);
    assertThat(search.description()).contains("Valor de la operación:");
    assertThat(search.facts()).as("search").isNotEmpty();
    assertThat(scenario("stack/simple/peek", 3).facts()).as("peek").isNotEmpty();
    // Si el algoritmo rechaza la instancia sorteada, el escenario viaja sin hechos en vez de
    // fallar.
    assertThat(scenario("tree/heap/extract", 3).description()).startsWith("Heap máximo construido");
  }

  @Test
  void everyCatalogTopicYieldsAUsableScenario() {
    for (var topic : topics.all()) {
      AlgorithmDescriptor descriptor = topics.find(topic.key()).orElseThrow();
      Scenario built = factory.build(descriptor, new Random(5), 2);
      assertThat(built.id()).isEqualTo(2);
      assertThat(built.description()).as(topic.key()).isNotBlank();
      assertThat(built.facts()).as(topic.key()).isNotNull();
      assertThat(topic.label()).contains(" — ");
      assertThat(topic.familyLabel()).isNotBlank();
    }
    assertThat(topics.find("no/existe/nunca")).isEmpty();
  }
}
