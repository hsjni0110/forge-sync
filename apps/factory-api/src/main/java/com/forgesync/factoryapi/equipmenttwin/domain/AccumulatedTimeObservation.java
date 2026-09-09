package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One uncorrected machine counter observation in seconds. */
public record AccumulatedTimeObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    AccumulatedTimeMetric metric,
    boolean isAvailable,
    BigDecimal valueSeconds) {

  public AccumulatedTimeObservation {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(metric, "metric");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    if (isAvailable != (valueSeconds != null)) {
      throw new IllegalArgumentException("Available counter observations require a value");
    }
    if (valueSeconds != null && valueSeconds.signum() < 0) {
      throw new IllegalArgumentException("Accumulated time must not be negative");
    }
  }
}
