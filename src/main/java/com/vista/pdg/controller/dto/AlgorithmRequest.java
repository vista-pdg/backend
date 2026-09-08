package com.vista.pdg.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;

/**
 * Petición de pasos. {@code values} alimenta los algoritmos que construyen su estructura (AVL,
 * pila, cola); {@code nodes}/{@code edges}/{@code start} (HU-19) alimentan los que recorren la
 * estructura que el estudiante ya tiene en el lienzo (BFS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlgorithmRequest(
    String type,
    String subtype,
    String operation,
    List<Integer> values,
    List<Node3D> nodes,
    List<Edge3D> edges,
    String start) {

  public AlgorithmRequest(String type, String subtype, String operation, List<Integer> values) {
    this(type, subtype, operation, values, null, null, null);
  }
}
