package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;
import java.util.Objects;

/** The observation that opened or closed an interval, so every boundary stays traceable. */
public record IntervalBoundaryEvidence(
    long replaySequence, Instant sourceObservedAt, String sourceEventKey) {

  public IntervalBoundaryEvidence {
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
  }

  static IntervalBoundaryEvidence of(StateSignalObservation observation) {
    return new IntervalBoundaryEvidence(
        observation.replaySequence(), observation.sourceObservedAt(), observation.sourceEventKey());
  }
}
