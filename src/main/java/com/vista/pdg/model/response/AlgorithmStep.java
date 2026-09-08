package com.vista.pdg.model.response;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;

/**
 * Un paso del rastro. {@code line} (HU-22a) es la línea 1-based del pseudocódigo de la respuesta
 * que este paso ejecuta; nula en los algoritmos no instrumentados y en el resumen final.
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
    Integer line) {

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
        null);
  }
}
