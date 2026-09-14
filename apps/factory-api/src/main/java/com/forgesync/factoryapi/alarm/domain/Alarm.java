package com.forgesync.factoryapi.alarm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Alarm(
    UUID alarmId,
    String machineId,
    UUID replaySessionId,
    long openedReplaySequence,
    Instant openedAt,
    String openedBySourceEventKey,
    String sourceDataItemId,
    String componentId,
    String conditionType,
    String nativeCode,
    String message,
    AlarmSeverity severity,
    AlarmStatus status,
    String ruleVersion,
    long revision,
    String acknowledgedBy,
    Instant acknowledgedAt,
    String resolvedBySourceEventKey,
    Long resolvedReplaySequence,
    Instant resolvedAt) {

  public Alarm {
    Objects.requireNonNull(alarmId, "alarmId");
    requireText(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (openedReplaySequence < 0) {
      throw new IllegalArgumentException("openedReplaySequence must not be negative");
    }
    Objects.requireNonNull(openedAt, "openedAt");
    requireText(openedBySourceEventKey, "openedBySourceEventKey");
    requireText(sourceDataItemId, "sourceDataItemId");
    requireText(componentId, "componentId");
    requireText(conditionType, "conditionType");
    requireText(nativeCode, "nativeCode");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(status, "status");
    requireText(ruleVersion, "ruleVersion");
    if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
    if (resolvedReplaySequence != null && resolvedReplaySequence < openedReplaySequence) {
      throw new IllegalArgumentException(
          "resolvedReplaySequence must not be before openedReplaySequence");
    }
  }

  public static Alarm open(UUID alarmId, AlarmCandidate candidate) {
    ConditionObservation condition = candidate.condition();
    return new Alarm(
        alarmId,
        condition.machineId(),
        condition.replaySessionId(),
        condition.replaySequence(),
        condition.sourceObservedAt(),
        condition.sourceEventKey(),
        condition.sourceDataItemId(),
        condition.componentId(),
        condition.conditionType(),
        condition.nativeCode(),
        condition.message(),
        candidate.severity(),
        AlarmStatus.OPEN,
        candidate.ruleVersion(),
        0,
        null,
        null,
        null,
        null,
        null);
  }

  public Alarm acknowledge(String operatorName, Instant acknowledgedAt) {
    if (status == AlarmStatus.RESOLVED) {
      throw new InvalidAlarmTransitionException("A resolved Alarm cannot be acknowledged");
    }
    if (status == AlarmStatus.ACKNOWLEDGED) return this;
    requireText(operatorName, "operatorName");
    Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
    return copy(
        AlarmStatus.ACKNOWLEDGED,
        revision + 1,
        operatorName.trim(),
        acknowledgedAt,
        null,
        null,
        null);
  }

  public Alarm resolve(String sourceEventKey, long resolvedReplaySequence, Instant resolvedAt) {
    if (status == AlarmStatus.RESOLVED) return this;
    requireText(sourceEventKey, "sourceEventKey");
    Objects.requireNonNull(resolvedAt, "resolvedAt");
    if (resolvedAt.isBefore(openedAt)) {
      throw new IllegalArgumentException("resolvedAt must not be before openedAt");
    }
    return copy(
        AlarmStatus.RESOLVED,
        revision + 1,
        acknowledgedBy,
        acknowledgedAt,
        sourceEventKey,
        resolvedReplaySequence,
        resolvedAt);
  }

  private Alarm copy(
      AlarmStatus nextStatus,
      long nextRevision,
      String nextAcknowledgedBy,
      Instant nextAcknowledgedAt,
      String nextResolvedBySourceEventKey,
      Long nextResolvedReplaySequence,
      Instant nextResolvedAt) {
    return new Alarm(
        alarmId,
        machineId,
        replaySessionId,
        openedReplaySequence,
        openedAt,
        openedBySourceEventKey,
        sourceDataItemId,
        componentId,
        conditionType,
        nativeCode,
        message,
        severity,
        nextStatus,
        ruleVersion,
        nextRevision,
        nextAcknowledgedBy,
        nextAcknowledgedAt,
        nextResolvedBySourceEventKey,
        nextResolvedReplaySequence,
        nextResolvedAt);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
