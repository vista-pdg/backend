package com.vista.pdg.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;

/**
 * Petición de pasos. {@code values} crea una demostración nueva. {@code nodes}/{@code edges}
 * ejecutan sobre la estructura del lienzo, también en algoritmos que admiten valores. {@code
 * argument} es el valor a insertar o buscar y {@code start} el nodo inicial del recorrido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlgorithmRequest(
    String type,
    String subtype,
    String operation,
    List<Integer> values,
    List<Node3D> nodes,
    List<Edge3D> edges,
    String start,
    Double argument) {

  public AlgorithmRequest(
      String type,
      String subtype,
      String operation,
      List<Integer> values,
      List<Node3D> nodes,
      List<Edge3D> edges,
      String start) {
    this(type, subtype, operation, values, nodes, edges, start, null);
  }

  public AlgorithmRequest(String type, String subtype, String operation, List<Integer> values) {
    this(type, subtype, operation, values, null, null, null, null);
  }
}
