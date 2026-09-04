package com.forgesync.factoryapi.replay.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ReplaySessionState(
    String schemaVersion,
    UUID replaySessionId,
    String machineId,
    String sourceSetId,
    String status,
    int speedMultiplier,
    long revision,
    SourceRange sourceRange,
    PublicationCursor publicationCursor,
    Failure failure) {
  private static final Set<String> STATUSES =
      Set.of("PREPARING", "RUNNING", "PAUSED", "SEEKING", "COMPLETED", "FAILED");

  public ReplaySessionState {
    if (!"1.0.0".equals(schemaVersion)) {
      throw new IllegalArgumentException("Unsupported Replay Session contract");
    }
    Objects.requireNonNull(replaySessionId);
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(sourceSetId);
    Objects.requireNonNull(status);
    Objects.requireNonNull(sourceRange);
    if (!machineId.matches("[A-Za-z0-9._-]{1,64}")
        || !sourceSetId.matches("[A-Za-z0-9._-]{1,128}")
        || !STATUSES.contains(status)
        || (speedMultiplier != 1 && speedMultiplier != 10 && speedMultiplier != 100)
        || revision < 0
        || sourceRange.startsAt().isAfter(sourceRange.endsAt())) {
      throw new IllegalArgumentException("Invalid Replay Session state");
    }
    if ((status.equals("FAILED")) != (failure != null)) {
      throw new IllegalArgumentException("Replay failure details must match FAILED status");
    }
  }

  public record SourceRange(Instant startsAt, Instant endsAt) {
    public SourceRange {
      Objects.requireNonNull(startsAt);
      Objects.requireNonNull(endsAt);
    }
  }

  public record PublicationCursor(
      long replaySequence, Instant sourceObservedAt, Instant replayPublishedAt) {
    public PublicationCursor {
      if (replaySequence < 0) {
        throw new IllegalArgumentException("Replay sequence must not be negative");
      }
      Objects.requireNonNull(sourceObservedAt);
      Objects.requireNonNull(replayPublishedAt);
    }
  }

  public record Failure(String code, String message, boolean retryable) {}
}
