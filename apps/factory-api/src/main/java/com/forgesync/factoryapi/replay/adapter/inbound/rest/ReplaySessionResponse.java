package com.forgesync.factoryapi.replay.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forgesync.factoryapi.replay.application.ReplaySessionState;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplaySessionResponse(
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

  static ReplaySessionResponse from(ReplaySessionState state) {
    return new ReplaySessionResponse(
        state.schemaVersion(),
        state.replaySessionId(),
        state.machineId(),
        state.sourceSetId(),
        state.status(),
        state.speedMultiplier(),
        state.revision(),
        new SourceRange(state.sourceRange().startsAt(), state.sourceRange().endsAt()),
        state.publicationCursor() == null
            ? null
            : new PublicationCursor(
                state.publicationCursor().replaySequence(),
                state.publicationCursor().sourceObservedAt(),
                state.publicationCursor().replayPublishedAt()),
        state.failure() == null
            ? null
            : new Failure(
                state.failure().code(), state.failure().message(), state.failure().retryable()));
  }

  public record SourceRange(Instant startsAt, Instant endsAt) {}

  public record PublicationCursor(
      long replaySequence, Instant sourceObservedAt, Instant replayPublishedAt) {}

  public record Failure(String code, String message, boolean retryable) {}
}
