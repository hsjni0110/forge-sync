package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One Canonical Observation of a state signal, as the interval policy consumes it. */
public record StateSignalObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    StateSignal signal,
    boolean isAvailable,
    String value) {

  public StateSignalObservation {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(signal, "signal");
    if (!isAvailable && value != null) {
      throw new IllegalArgumentException("Unavailable observations cannot carry a value");
    }
    if (isAvailable && (value == null || value.isBlank())) {
      throw new IllegalArgumentException("Available observations require a value");
    }
  }
}
