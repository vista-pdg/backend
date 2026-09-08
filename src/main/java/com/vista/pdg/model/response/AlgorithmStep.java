package com.vista.pdg.model.response;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;
import java.util.Map;

/**
 * Un paso del rastro.
 *
 * <p>{@code line} (HU-22a) es la línea 1-based del pseudocódigo de la respuesta que este paso
 * ejecuta. {@code variables} (HU-22b) son los valores vigentes de las variables del algoritmo, ya
 * serializados como texto legible y en el orden en que el algoritmo los declara; {@code callStack}
 * (HU-22b) son los marcos activos, la base primero y el tope al final. Los tres son nulos en los
 * algoritmos sin instrumentar y en el resumen final.
 */
public record AlgorithmStep(
    int index,
    String title,
    String description,
    String highlightType,
    List<String> highlightedNodeIds,
    String rotationType,
    List<Node3D> nodes,
    List<Edge3D> edges,
    Integer line,
    Map<String, String> variables,
    List<Frame> callStack) {

  /** Un marco de la pila de llamadas: nombre de la función y sus parámetros. */
  public record Frame(String name, Map<String, String> params) {}

  public AlgorithmStep(
      int index,
      String title,
      String description,
      String highlightType,
      List<String> highlightedNodeIds,
      String rotationType,
      List<Node3D> nodes,
      List<Edge3D> edges) {
    this(
        index,
        title,
        description,
        highlightType,
        highlightedNodeIds,
        rotationType,
        nodes,
        edges,
        null,
        null,
        null);
  }

  public AlgorithmStep(
      int index,
      String title,
      String description,
      String highlightType,
      List<String> highlightedNodeIds,
      String rotationType,
      List<Node3D> nodes,
      List<Edge3D> edges,
      Integer line) {
    this(
        index,
        title,
        description,
        highlightType,
        highlightedNodeIds,
        rotationType,
        nodes,
        edges,
        line,
        null,
        null);
  }
}
