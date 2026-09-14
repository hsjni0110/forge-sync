package com.forgesync.factoryapi.alarm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConditionObservation(
    String machineId,
    UUID replaySessionId,
    long replaySequence,
    Instant sourceObservedAt,
    String sourceEventKey,
    String sourceDataItemId,
    String componentId,
    String conditionType,
    String level,
    String nativeCode,
    String message) {

  public ConditionObservation {
    requireText(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (replaySequence < 0)
      throw new IllegalArgumentException("replaySequence must not be negative");
    Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
    requireText(sourceEventKey, "sourceEventKey");
    requireText(sourceDataItemId, "sourceDataItemId");
    requireText(componentId, "componentId");
    requireText(conditionType, "conditionType");
    requireText(level, "level");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}
