package com.forgesync.factoryapi.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValidatedObservationMessage(
    String observationJson,
    UUID eventId,
    String machineId,
    String componentId,
    String observationKind,
    Instant sourceObservedAt,
    UUID replaySessionId,
    long replaySequence,
    Instant replayPublishedAt,
    String sourceEventKey,
    String artifactId,
    String rawRecordId,
    String mappingVersion,
    String sourceDataItemId) {

  public ValidatedObservationMessage {
    Objects.requireNonNull(observationJson, "observationJson");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(componentId, "componentId");
    Objects.requireNonNull(observationKind, "observationKind");
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0) {
      throw new IllegalArgumentException("replaySequence must not be negative");
    }
    Objects.requireNonNull(replayPublishedAt, "replayPublishedAt");
    Objects.requireNonNull(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(artifactId, "artifactId");
    Objects.requireNonNull(rawRecordId, "rawRecordId");
    Objects.requireNonNull(mappingVersion, "mappingVersion");
    Objects.requireNonNull(sourceDataItemId, "sourceDataItemId");
  }
}
