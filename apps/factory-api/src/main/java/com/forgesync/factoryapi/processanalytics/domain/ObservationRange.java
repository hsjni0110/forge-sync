package com.forgesync.factoryapi.processanalytics.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ObservationRange(
    UUID replaySessionId,
    long firstReplaySequence,
    long lastReplaySequence,
    Instant firstSourceObservedAt,
    Instant lastSourceObservedAt,
    String firstSourceEventKey,
    String lastSourceEventKey) {

  public ObservationRange {
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (firstReplaySequence < 0 || lastReplaySequence < firstReplaySequence) {
      throw new IllegalArgumentException("Invalid observation range sequence");
    }
    Objects.requireNonNull(firstSourceObservedAt, "firstSourceObservedAt");
    Objects.requireNonNull(lastSourceObservedAt, "lastSourceObservedAt");
    Objects.requireNonNull(firstSourceEventKey, "firstSourceEventKey");
    Objects.requireNonNull(lastSourceEventKey, "lastSourceEventKey");
  }
}
