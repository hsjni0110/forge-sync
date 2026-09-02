package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ObservationOrder(
    UUID replaySessionId, long replaySequence, Instant sourceObservedAt, String sourceEventKey) {

  public ObservationOrder {
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    if (sourceEventKey.isBlank()) {
      throw new IllegalArgumentException("sourceEventKey must not be blank");
    }
  }
}
