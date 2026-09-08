package com.vista.pdg.service.algorithm.impl;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.model.response.AlgorithmStep;
import com.vista.pdg.model.response.StepsResponse;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.service.algorithm.def.AlgorithmStrategy;
import com.vista.pdg.service.generator.impl.QueueGenerator;
import com.vista.pdg.service.layout.impl.LinearLayout3D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@code dequeue} hasta vaciar la cola (HU-19). Dos pasos por retiro, como {@code pop}: el frente
 * resaltado y luego la cola sin él. Los ids se reasignan desde el frente en cada instantánea
 * ({@code n0} es siempre el frente), igual que hace el generador.
 */
@Service
public class QueueDequeueAlgorithm implements AlgorithmStrategy {

  private static final AlgorithmDescriptor DESCRIPTOR =
      new AlgorithmDescriptor(
          "queue",
          "simple",
          "dequeue",
          "queue",
          "Dequeue · Retiro del frente",
          "Retira los elementos uno a uno desde el frente; dos pasos por elemento",
          "values");

  private final LinearLayout3D layout;

  public QueueDequeueAlgorithm(LinearLayout3D layout) {
    this.layout = layout;
  }

  @Override
  public AlgorithmDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public StepsResponse generate(AlgorithmRequest req) {
    if (req.values() == null || req.values().isEmpty()) {
      return StepsResponse.error("Se requiere al menos un valor en la cola");
    }
    List<Integer> queue = new ArrayList<>(req.values());
    List<AlgorithmStep> steps = new ArrayList<>();
    steps.add(
        step(
            steps.size(),
            "Cola con " + queue.size() + " elementos",
            "Estado inicial. Frente: " + queue.getFirst() + ", final: " + queue.getLast() + ".",
            "initial",
            List.of(),
            queue));
    while (!queue.isEmpty()) {
      int front = queue.getFirst();
      steps.add(
          step(
              steps.size(),
              "dequeue(): frente " + front,
              "El frente es " + front + ". Sale el primero que entró.",
              "dequeue",
              List.of("n0"),
              queue));
      queue.removeFirst();
      steps.add(
          step(
              steps.size(),
              front + " retirado",
              queue.isEmpty()
                  ? "La cola quedó vacía."
                  : "Quedan "
                      + queue.size()
                      + " elementos. Nuevo frente: "
                      + queue.getFirst()
                      + ".",
              "done",
              queue.isEmpty() ? List.of() : List.of("n0"),
              queue));
    }
    return StepsResponse.ok(steps);
  }

  private AlgorithmStep step(
      int index,
      String title,
      String description,
      String type,
      List<String> hl,
      List<Integer> values) {
    List<Node3D> nodes = QueueGenerator.queueNodes(values);
    List<Edge3D> edges = QueueGenerator.queueEdges(values.size());
    Map<String, Vec3> pos = layout.compute(new GeneratedStructure(null, nodes, edges, Map.of()));
    List<Node3D> placed =
        nodes.stream()
            .map(
                n -> {
                  Vec3 p = pos.getOrDefault(n.id(), Vec3.zero());
                  return n.withPosition(p.x(), p.y(), p.z());
                })
            .toList();
    return new AlgorithmStep(index, title, description, type, hl, null, placed, edges);
  }
}
