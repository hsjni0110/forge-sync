package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReplayCursor(
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    Instant replayPublishedAt,
    TwinVersion twinVersion) {

  public ReplayCursor {
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(replayPublishedAt, "replayPublishedAt");
    Objects.requireNonNull(twinVersion, "twinVersion");
  }
}
