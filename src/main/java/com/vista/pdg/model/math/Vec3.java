package com.vista.pdg.model.math;

public record Vec3(double x, double y, double z) {
  public static Vec3 of(double x, double y, double z) {
    return new Vec3(x, y, z);
  }

  public static Vec3 zero() {
    return new Vec3(0, 0, 0);
  }
}
