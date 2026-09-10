package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PartCountObservation(
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    boolean isAvailable,
    BigDecimal value) {
  public PartCountObservation {
    Objects.requireNonNull(sourceObservedAt);
    Objects.requireNonNull(sourceEventKey);
    if (replaySequence < 0
        || isAvailable != (value != null)
        || value != null && value.signum() < 0) {
      throw new IllegalArgumentException("Part count observation is invalid");
    }
  }
}
