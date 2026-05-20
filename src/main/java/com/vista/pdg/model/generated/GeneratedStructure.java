package com.vista.pdg.model.generated;

import com.vista.pdg.model.contract.def.StructureContract;
import java.util.List;
import java.util.Map;

public record GeneratedStructure(
    StructureContract contract,
    List<Node3D> nodes,
    List<Edge3D> edges,
    Map<String, Object> computedProperties) {}
