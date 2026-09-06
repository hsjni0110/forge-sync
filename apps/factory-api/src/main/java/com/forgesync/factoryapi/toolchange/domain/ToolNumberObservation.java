package com.forgesync.factoryapi.toolchange.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ToolNumberObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    Long value,
    String sourceDataItemId,
    String sourceSetId,
    String artifactId,
    String rawRecordId,
    String mappingVersion) {
  public ToolNumberObservation {
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(replaySessionId);
    Objects.requireNonNull(sourceObservedAt);
    Objects.requireNonNull(sourceDataItemId);
    Objects.requireNonNull(sourceSetId);
    Objects.requireNonNull(artifactId);
    Objects.requireNonNull(rawRecordId);
    Objects.requireNonNull(mappingVersion);
    if (replaySequence < 0)
      throw new IllegalArgumentException("replaySequence must not be negative");
  }

  public boolean isAvailable() {
    return value != null;
  }
}
