package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.generator.impl.StackGenerator;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@code pop} hasta vaciar la pila (HU-19 · CA-5), instrumentado (HU-22b). Dos pasos por retiro:
 * primero el tope queda resaltado ({@code pop}, línea 3) y en el siguiente ya no está (línea 4).
 * Las variables son {@code tope}, {@code tamaño} y {@code retirados}.
 */
@Service
public class StackPopAlgorithm implements AlgorithmStrategy {

  public static final List<String> CODE =
      List.of(
          "vaciar(pila):",
          "  mientras pila no esté vacía:",
          "    tope ← pila.cima()",
          "    pila.retirar()   // pop",
          "    retirados.añadir(tope)",
          "  retornar retirados");

  private static final AlgorithmDescriptor DESCRIPTOR =
      new AlgorithmDescriptor(
          "stack",
          "simple",
          "pop",
          "stack",
          "Pop · Retiro del tope",
          "Retira los elementos uno a uno desde el tope; dos pasos por elemento",
          "values");

  private final StackLayout3D layout;

  public StackPopAlgorithm(StackLayout3D layout) {
    this.layout = layout;
  }

  @Override
  public AlgorithmDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public StepsResponse generate(AlgorithmRequest req) {
    if (req.values() == null || req.values().isEmpty()) {
      return StepsResponse.error("Se requiere al menos un valor en la pila");
    }
    List<Integer> stack = new ArrayList<>(req.values());
    List<Integer> removed = new ArrayList<>();
    List<AlgorithmStep> steps = new ArrayList<>();
    steps.add(
        step(
            steps.size(),
            "Pila con " + stack.size() + " elementos",
            "Estado inicial. Tope: " + stack.getLast() + ", base: " + stack.getFirst() + ".",
            "initial",
            List.of(),
            stack,
            1,
            vars("—", stack.size(), removed)));
    while (!stack.isEmpty()) {
      int top = stack.getLast();
      String topId = "n" + (stack.size() - 1);
      steps.add(
          step(
              steps.size(),
              "pop(): tope " + top,
              "El tope es " + top + ". Es el único elemento accesible: se retira a continuación.",
              "pop",
              List.of(topId),
              stack,
              3,
              vars(String.valueOf(top), stack.size(), removed)));
      stack.removeLast();
      removed.add(top);
      steps.add(
          step(
              steps.size(),
              top + " retirado",
              stack.isEmpty()
                  ? "La pila quedó vacía."
                  : "Quedan " + stack.size() + " elementos. Nuevo tope: " + stack.getLast() + ".",
              "done",
              stack.isEmpty() ? List.of() : List.of("n" + (stack.size() - 1)),
              stack,
              4,
              vars(String.valueOf(top), stack.size(), removed)));
    }
    return StepsResponse.ok(steps, CODE, "pseudocode");
  }

  private static Map<String, String> vars(String top, int size, List<Integer> removed) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("tope", top);
    m.put("tamaño", String.valueOf(size));
    m.put("retirados", removed.toString());
    return m;
  }

  private AlgorithmStep step(
      int index,
      String title,
      String description,
      String type,
      List<String> hl,
      List<Integer> values,
      int line,
      Map<String, String> variables) {
    List<Node3D> nodes = StackGenerator.stackNodes(values);
    Map<String, Vec3> pos =
        layout.compute(new GeneratedStructure(null, nodes, List.of(), Map.of()));
    List<Node3D> placed =
        nodes.stream()
            .map(
                n -> {
                  Vec3 p = pos.getOrDefault(n.id(), Vec3.zero());
                  return n.withPosition(p.x(), p.y(), p.z());
                })
            .toList();
    return new AlgorithmStep(
        index, title, description, type, hl, null, placed, List.of(), line, variables, null);
  }
}
