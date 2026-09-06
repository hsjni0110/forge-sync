package com.forgesync.factoryapi.equipmenttwin.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.SpatialLayout;
import com.forgesync.factoryapi.equipmenttwin.application.SpatialLayoutProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonSpatialLayoutProvider implements SpatialLayoutProvider {
  private final Map<String, SpatialLayout> layouts;

  public JsonSpatialLayoutProvider(ObjectMapper objectMapper, InputStream input) {
    try (input) {
      Document document = objectMapper.readValue(input, Document.class);
      if (!"1.0.0".equals(document.schemaVersion())) {
        throw new IllegalArgumentException("Unsupported spatial layout version");
      }
      Map<String, SpatialLayout> validLayouts = new HashMap<>();
      for (Machine machine : document.machines()) {
        try {
          validLayouts.put(
              machine.machineId(),
              new SpatialLayout(
                  machine.assetId(),
                  machine.sceneNodeId(),
                  machine.position(),
                  document.positionUnit(),
                  machine.rotation(),
                  document.rotationUnit(),
                  machine.scale(),
                  machine.provenance()));
        } catch (IllegalArgumentException ignored) {
          // A partial layout is unavailable as a whole; other valid machines remain usable.
        }
      }
      layouts = Map.copyOf(validLayouts);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read spatial layout configuration", exception);
    }
  }

  @Override
  public Optional<SpatialLayout> findByMachineId(String machineId) {
    return Optional.ofNullable(layouts.get(machineId));
  }

  private record Document(
      String schemaVersion, String positionUnit, String rotationUnit, List<Machine> machines) {}

  private record Machine(
      String machineId,
      String assetId,
      String sceneNodeId,
      List<Double> position,
      List<Double> rotation,
      List<Double> scale,
      String provenance) {}
}
