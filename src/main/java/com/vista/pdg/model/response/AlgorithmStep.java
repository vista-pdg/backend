package com.vista.pdg.model.response;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.Node3D;
import java.util.List;

public record AlgorithmStep(
    int index,
    String title,
    String description,
    String highlightType,
    List<String> highlightedNodeIds,
    String rotationType,
    List<Node3D> nodes,
    List<Edge3D> edges) {}
