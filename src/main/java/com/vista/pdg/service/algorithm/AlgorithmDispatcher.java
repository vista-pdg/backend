package com.vista.pdg.service.algorithm;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmDispatcher {

  private final Map<String, AlgorithmStrategy> strategies = new LinkedHashMap<>();

  public AlgorithmDispatcher(List<AlgorithmStrategy> all) {
    all.stream()
        .sorted(Comparator.comparing((AlgorithmStrategy s) -> s.descriptor().key()))
        .forEach(s -> strategies.put(s.descriptor().key(), s));
  }

  /** El catálogo no depende de nada del cliente: es el mismo en 2D y en 3D (HU-19 · CA-4). */
  public List<AlgorithmDescriptor> catalog() {
    return strategies.values().stream().map(AlgorithmStrategy::descriptor).toList();
  }

  public StepsResponse run(AlgorithmRequest request) {
    String key = request.type() + "/" + request.subtype() + "/" + request.operation();
    AlgorithmStrategy strategy = strategies.get(key);
    if (strategy == null) return StepsResponse.error("Algoritmo no soportado: " + key);
    return strategy.generate(request);
  }
}
