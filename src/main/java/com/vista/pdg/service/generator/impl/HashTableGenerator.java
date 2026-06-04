package com.vista.pdg.service.generator.impl;

import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HashTableGenerator implements StructureGenerator<HashTableContract> {

  @Override
  public String supportedType() {
    return "hash-table";
  }

  @Override
  public GeneratedStructure generate(HashTableContract contract) {
    int size = contract.size();
    List<Integer> values = contract.values();

    List<Node3D> nodes = new ArrayList<>();
    List<Edge3D> edges = new ArrayList<>();

    // Bucket nodes (depth 0, no parent)
    for (int i = 0; i < size; i++) {
      nodes.add(
          new Node3D("b" + i, "[" + i + "]", 0, 0, 0, 0, null, Map.of("bucket", true)));
    }

    // Distribute values into buckets using separate chaining
    List<List<Integer>> buckets = new ArrayList<>();
    for (int i = 0; i < size; i++) buckets.add(new ArrayList<>());
    for (int v : values) buckets.get(Math.abs(v % size)).add(v);

    int valIdx = 0;
    int edgeIdx = 0;
    for (int b = 0; b < size; b++) {
      String prevId = "b" + b;
      for (int pos = 0; pos < buckets.get(b).size(); pos++) {
        int v = buckets.get(b).get(pos);
        String vId = "v" + valIdx++;
        nodes.add(
            new Node3D(vId, String.valueOf(v), 0, 0, 0, pos + 1, prevId, Map.of("hashBucket", b)));
        edges.add(new Edge3D("e" + edgeIdx++, prevId, vId, null, true));
        prevId = vId;
      }
    }

    return new GeneratedStructure(
        contract, nodes, edges, Map.of("bucketCount", size, "loadFactor", (double) values.size() / size));
  }
}
