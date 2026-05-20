package com.vista.pdg.model.generated;

import java.util.Map;

public record Node3D(
    String id,
    String label,
    double x,
    double y,
    double z,
    int depth,
    String parent,
    Map<String, Object> properties
) {
    public Node3D withPosition(double x, double y, double z) {
        return new Node3D(id, label, x, y, z, depth, parent, properties);
    }
}
