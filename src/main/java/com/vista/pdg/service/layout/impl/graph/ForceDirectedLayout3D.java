package com.vista.pdg.service.layout.impl.graph;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ForceDirectedLayout3D implements LayoutStrategy {

  private static final int ITERATIONS = 75;
  private static final double K = 2.0;
  private static final double INITIAL_TEMP = 5.0;

  @Override
  public String supportedLayout() {
    return "force3d";
  }

  @Override
  public Map<String, Vec3> compute(GeneratedStructure structure) {
    List<Node3D> nodes = structure.nodes();
    if (nodes.isEmpty()) return Map.of();

    Random rand = new Random(42);
    Map<String, double[]> pos = new HashMap<>();
    for (Node3D n : nodes) {
      pos.put(
          n.id(),
          new double[] {
            (rand.nextDouble() - 0.5) * 10,
            (rand.nextDouble() - 0.5) * 10,
            (rand.nextDouble() - 0.5) * 10
          });
    }

    double temp = INITIAL_TEMP;
    double cooling = temp / ITERATIONS;

    for (int iter = 0; iter < ITERATIONS; iter++) {
      Map<String, double[]> disp = new HashMap<>();
      for (Node3D n : nodes) disp.put(n.id(), new double[3]);

      // Repulsion between all pairs
      for (int i = 0; i < nodes.size(); i++) {
        for (int j = i + 1; j < nodes.size(); j++) {
          String a = nodes.get(i).id(), b = nodes.get(j).id();
          double[] delta = diff(pos.get(a), pos.get(b));
          double dist = Math.max(0.01, magnitude(delta));
          double force = (K * K) / dist;
          addScaled(disp.get(a), delta, force / dist);
          addScaled(disp.get(b), delta, -force / dist);
        }
      }

      // Attraction along edges
      for (Edge3D e : structure.edges()) {
        double[] delta = diff(pos.get(e.from()), pos.get(e.to()));
        double dist = Math.max(0.01, magnitude(delta));
        double force = (dist * dist) / K;
        addScaled(disp.get(e.from()), delta, -force / dist);
        addScaled(disp.get(e.to()), delta, force / dist);
      }

      // Apply with temperature cap
      for (Node3D n : nodes) {
        double[] d = disp.get(n.id());
        double dmag = Math.max(0.01, magnitude(d));
        double scale = Math.min(dmag, temp) / dmag;
        double[] p = pos.get(n.id());
        p[0] += d[0] * scale;
        p[1] += d[1] * scale;
        p[2] += d[2] * scale;
      }
      temp = Math.max(0.01, temp - cooling);
    }

    Map<String, Vec3> result = new HashMap<>();
    for (Node3D n : nodes) {
      double[] p = pos.get(n.id());
      result.put(n.id(), Vec3.of(p[0], p[1], p[2]));
    }
    return result;
  }

  private double[] diff(double[] a, double[] b) {
    return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
  }

  private double magnitude(double[] v) {
    return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
  }

  private void addScaled(double[] target, double[] v, double scale) {
    target[0] += v[0] * scale;
    target[1] += v[1] * scale;
    target[2] += v[2] * scale;
  }
}
