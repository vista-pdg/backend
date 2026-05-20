package com.vista.pdg.model.response;

import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import java.util.List;
import java.util.Map;

public record StructureResponse(
    boolean error,
    String message,
    List<AttemptDetail> attempts,
    StructureContract contract,
    List<Node3D> nodes,
    List<Edge3D> edges,
    Meta meta) {

  public record Meta(
      String type,
      String subtype,
      int nodeCount,
      int edgeCount,
      Map<String, Object> computedProperties) {}

  public record AttemptDetail(int attempt, String error) {}

  public static StructureResponse of(
      StructureContract contract, GeneratedStructure structure, Map<String, Vec3> positions) {

    List<Node3D> positionedNodes =
        structure.nodes().stream()
            .map(
                n -> {
                  Vec3 pos = positions.getOrDefault(n.id(), Vec3.zero());
                  return n.withPosition(pos.x(), pos.y(), pos.z());
                })
            .toList();

    String subtype = extractSubtype(contract);

    Meta meta =
        new Meta(
            contract.type(),
            subtype,
            positionedNodes.size(),
            structure.edges().size(),
            structure.computedProperties());

    return new StructureResponse(
        false, null, null, contract, positionedNodes, structure.edges(), meta);
  }

  public static StructureResponse error(String message, List<AttemptDetail> attempts) {
    return new StructureResponse(true, message, attempts, null, null, null, null);
  }

  private static String extractSubtype(StructureContract contract) {
    return switch (contract) {
      case com.vista.pdg.model.contract.TreeContract t -> t.subtype();
      case com.vista.pdg.model.contract.LatticeContract l -> l.subtype();
      default -> null;
    };
  }
}
