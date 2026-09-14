package com.forgesync.factoryapi.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ObservedProductionContext(
    String ruleVersion,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String machiningRunProcessingRunId,
    List<ProgramInterval> programIntervals,
    List<ProgramSummary> programSummaries,
    int unassignedRunCount,
    PartCountSummary partCount) {
  public record ProgramObservation(
      String kind,
      boolean isAvailable,
      String value,
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId) {}

  public record ObservedMachiningRun(
      String machiningRunId,
      String programName,
      String status,
      Instant startedAt,
      Instant endedAt) {}

  public record PartCountObservation(
      boolean isAvailable,
      BigDecimal value,
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId) {}

  public record Evidence(
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId) {}

  public record ProgramInterval(
      String kind,
      String availability,
      String programName,
      Instant startedAt,
      Instant endedAt,
      Evidence startEvidence,
      Evidence endEvidence) {}

  public record ProgramSummary(
      String programName,
      int runCount,
      int completedRunCount,
      BigDecimal totalDurationSeconds,
      BigDecimal meanDurationSeconds,
      BigDecimal medianDurationSeconds,
      List<String> machiningRunIds) {}

  public record PartCountAssociation(
      BigDecimal observedIncrease,
      Evidence fromEvidence,
      Evidence toEvidence,
      String relationship,
      List<String> overlappingMachiningRunIds) {}

  public record PartCountSummary(
      String status,
      BigDecimal netIncrease,
      int usedTransitionCount,
      int resetCount,
      int unavailableObservationCount,
      String reason,
      List<PartCountAssociation> associations) {}
}
