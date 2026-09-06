package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.List;
import java.util.Objects;

public record SpatialLayout(
    String assetId,
    String sceneNodeId,
    List<Double> position,
    String positionUnit,
    List<Double> rotation,
    String rotationUnit,
    List<Double> scale,
    String provenance) {

  public SpatialLayout {
    Objects.requireNonNull(assetId);
    Objects.requireNonNull(sceneNodeId);
    position = vector(position, false);
    rotation = vector(rotation, false);
    scale = vector(scale, true);
    if (!"SCENE_UNIT".equals(positionUnit)
        || !"RADIAN".equals(rotationUnit)
        || !"SIMULATED_LAYOUT".equals(provenance)) {
      throw new IllegalArgumentException("Unsupported spatial semantics");
    }
  }

  private static List<Double> vector(List<Double> values, boolean positive) {
    if (values == null
        || values.size() != 3
        || values.stream().anyMatch(value -> value == null || !Double.isFinite(value))
        || (positive && values.stream().anyMatch(value -> value <= 0))) {
      throw new IllegalArgumentException("Spatial vectors must contain three valid values");
    }
    return List.copyOf(values);
  }
}
