package com.vista.pdg.service.generator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import com.vista.pdg.service.sdd.impl.validator.QueueContractValidator;
import com.vista.pdg.service.sdd.impl.validator.StackContractValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-19: las dos familias nuevas del syllabus, del contrato al modelo y a la disposición 3D. */
class StackQueueGeneratorTest {

  private final StackGenerator stacks = new StackGenerator();
  private final QueueGenerator queues = new QueueGenerator();

  @Test
  @DisplayName("la pila produce un nodo por valor, sin aristas, y marca base y tope")
  void stackNodesRolesAndNoEdges() {
    GeneratedStructure s = stacks.generate(new StackContract("stack", null, List.of(3, 42, 8, 17)));
    assertThat(s.nodes()).extracting(Node3D::label).containsExactly("3", "42", "8", "17");
    assertThat(s.edges()).isEmpty();
    assertThat(s.nodes().getFirst().properties()).containsEntry("role", "bottom");
    assertThat(s.nodes().getLast().properties()).containsEntry("role", "top");
    assertThat(s.nodes().get(1).properties())
        .containsEntry("role", "middle")
        .containsEntry("index", 1);
    assertThat(s.computedProperties()).containsEntry("size", 4).containsEntry("top", 17);
  }

  @Test
  @DisplayName("una pila de un elemento: ese elemento es el tope")
  void singleElementStackIsTop() {
    GeneratedStructure s = stacks.generate(new StackContract("stack", null, List.of(7)));
    assertThat(s.nodes()).hasSize(1);
    assertThat(s.nodes().getFirst().properties()).containsEntry("role", "top");
  }

  @Test
  @DisplayName("la cola encadena del frente al final con aristas dirigidas")
  void queueEdgesFrontToRear() {
    GeneratedStructure q = queues.generate(new QueueContract("queue", null, List.of(5, 9, 1, 14)));
    assertThat(q.nodes()).extracting(Node3D::label).containsExactly("5", "9", "1", "14");
    assertThat(q.edges()).hasSize(3);
    assertThat(q.edges().getFirst().from()).isEqualTo("n0");
    assertThat(q.edges().getFirst().to()).isEqualTo("n1");
    assertThat(q.edges()).allMatch(e -> e.directed());
    assertThat(q.nodes().getFirst().properties()).containsEntry("role", "front");
    assertThat(q.nodes().getLast().properties()).containsEntry("role", "rear");
    assertThat(q.computedProperties()).containsEntry("front", 5).containsEntry("rear", 14);
  }

  @Test
  @DisplayName("el layout stack3d apila hacia arriba en Y, centrado en el origen")
  void stackLayoutGrowsUpwards() {
    GeneratedStructure s = stacks.generate(new StackContract("stack", null, List.of(1, 2, 3)));
    Map<String, Vec3> pos = new StackLayout3D().compute(s);
    assertThat(pos.get("n0").y()).isLessThan(pos.get("n1").y());
    assertThat(pos.get("n1").y()).isLessThan(pos.get("n2").y());
    assertThat(pos.get("n1").y()).isEqualTo(0.0);
    assertThat(pos.values()).allMatch(v -> v.x() == 0 && v.z() == 0);
  }

  @Test
  @DisplayName("los validadores rechazan pilas y colas vacías o con nulos")
  void validatorsRejectEmptyAndNulls() {
    StackContractValidator sv = new StackContractValidator();
    QueueContractValidator qv = new QueueContractValidator();
    assertThatThrownBy(() -> sv.validate(new StackContract("stack", null, List.of())))
        .isInstanceOf(InvalidContractException.class);
    assertThatThrownBy(() -> sv.validate(new StackContract("stack", null, null)))
        .isInstanceOf(InvalidContractException.class);
    assertThatThrownBy(() -> qv.validate(new QueueContract("queue", null, List.of())))
        .isInstanceOf(InvalidContractException.class);
    java.util.List<Integer> withNull = new java.util.ArrayList<>();
    withNull.add(1);
    withNull.add(null);
    assertThatThrownBy(() -> qv.validate(new QueueContract("queue", null, withNull)))
        .isInstanceOf(InvalidContractException.class);
    sv.validate(new StackContract("stack", null, List.of(1)));
    qv.validate(new QueueContract("queue", null, List.of(1, 2)));
  }
}
