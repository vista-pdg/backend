package com.vista.pdg.service.generator.impl;

import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Pila (HU-19). Un nodo por elemento, {@code n0} en la base y el último en el tope; sin aristas.
 * {@code properties.index} y {@code properties.role} ({@code bottom} / {@code top} / {@code
 * middle}) son lo que los renderizadores usan para apilar y para etiquetar el tope.
 */
@Service
public class StackGenerator implements StructureGenerator<StackContract> {

  @Override
  public String supportedType() {
    return "stack";
  }

  @Override
  public GeneratedStructure generate(StackContract contract) {
    return new GeneratedStructure(
        contract,
        stackNodes(contract.values()),
        List.of(),
        Map.of("size", contract.values().size(), "top", contract.values().getLast()));
  }

  /** Compartido con el algoritmo {@code pop}, que reconstruye la pila en cada paso. */
  public static List<Node3D> stackNodes(List<Integer> values) {
    int n = values.size();
    return java.util.stream.IntStream.range(0, n)
        .mapToObj(
            i ->
                new Node3D(
                    "n" + i,
                    String.valueOf(values.get(i)),
                    0,
                    0,
                    0,
                    0,
                    null,
                    Map.of("index", i, "role", roleOf(i, n))))
        .toList();
  }

  private static String roleOf(int i, int n) {
    if (i == n - 1) return "top";
    if (i == 0) return "bottom";
    return "middle";
  }
}
