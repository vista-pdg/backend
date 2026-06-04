package com.vista.pdg.controller;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.AvlStepsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/algorithm")
public class AlgorithmController {

  private final AvlStepsService avlStepsService;

  public AlgorithmController(AvlStepsService avlStepsService) {
    this.avlStepsService = avlStepsService;
  }

  @PostMapping("/steps")
  public ResponseEntity<StepsResponse> steps(@RequestBody AlgorithmRequest req) {
    if ("tree".equals(req.type()) && "avl".equals(req.subtype()) && "insert".equals(req.operation())) {
      return ResponseEntity.ok(avlStepsService.generateAvlInsertSteps(req.values()));
    }
    return ResponseEntity.ok(
        StepsResponse.error(
            "Algoritmo no soportado: " + req.type() + "/" + req.subtype() + "/" + req.operation()));
  }
}
