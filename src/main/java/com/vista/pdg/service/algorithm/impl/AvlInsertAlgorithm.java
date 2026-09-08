package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AvlStepsService;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import org.springframework.stereotype.Service;

@Service
public class AvlInsertAlgorithm implements AlgorithmStrategy {

  private static final AlgorithmDescriptor DESCRIPTOR =
      new AlgorithmDescriptor(
          "tree",
          "avl",
          "insert",
          "tree",
          "AVL · Inserción",
          "Inserciones sucesivas con rotaciones simples y dobles",
          "values");

  private final AvlStepsService avl;

  public AvlInsertAlgorithm(AvlStepsService avl) {
    this.avl = avl;
  }

  @Override
  public AlgorithmDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public StepsResponse generate(AlgorithmRequest request) {
    return avl.generateAvlInsertSteps(request.values());
  }
}
