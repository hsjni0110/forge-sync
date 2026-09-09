package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConditionEvidenceObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    String componentId,
    String conditionType,
    String level,
    String nativeCode,
    String message) {

  public ConditionEvidenceObservation {
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(replaySessionId);
    Objects.requireNonNull(sourceObservedAt);
    Objects.requireNonNull(sourceEventKey);
    Objects.requireNonNull(componentId);
    Objects.requireNonNull(conditionType);
    Objects.requireNonNull(level);
  }
}
