package com.vista.pdg.service.generator.impl;

import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LinkedListGenerator implements StructureGenerator<LinkedListContract> {

  @Override
  public String supportedType() {
    return "linked-list";
  }

  @Override
  public GeneratedStructure generate(LinkedListContract contract) {
    List<Integer> values = contract.values();
    String subtype = contract.subtype() != null ? contract.subtype() : "singly";

    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();

    for (int i = 0; i < values.size(); i++) {
      nodes.add(new Node3D("n" + i, String.valueOf(values.get(i)), 0, 0, 0, 0, null, Map.of()));
    }

    int edgeIdx = 0;
    for (int i = 0; i < values.size() - 1; i++) {
      edges.add(new Edge3D("e" + edgeIdx++, "n" + i, "n" + (i + 1), null, true));
      if ("doubly".equals(subtype)) {
        edges.add(new Edge3D("e" + edgeIdx++, "n" + (i + 1), "n" + i, null, true));
      }
    }

    if ("circular".equals(subtype) && values.size() > 1) {
      edges.add(new Edge3D("e" + edgeIdx, "n" + (values.size() - 1), "n0", null, true));
    }

    return new GeneratedStructure(contract, nodes, edges, Map.of("subtype", subtype));
  }
}
