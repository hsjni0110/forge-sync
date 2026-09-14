package com.forgesync.factoryapi.production.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObservedProductionContextResponse(
    String schemaVersion,
    String ruleVersion,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String machiningRunProcessingRunId,
    List<ProgramInterval> programIntervals,
    List<ProgramSummary> programSummaries,
    int unassignedRunCount,
    PartCount partCount,
    String productionResultStatus) {
  public record Evidence(
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ProgramInterval(
      String kind,
      String availability,
      String programName,
      Instant startedAt,
      Instant endedAt,
      Evidence startEvidence,
      Evidence endEvidence) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ProgramSummary(
      String programName,
      int runCount,
      int completedRunCount,
      BigDecimal totalDurationSeconds,
      BigDecimal meanDurationSeconds,
      BigDecimal medianDurationSeconds,
      List<String> machiningRunIds) {}

  public record Association(
      BigDecimal observedIncrease,
      Evidence fromEvidence,
      Evidence toEvidence,
      String relationship,
      List<String> overlappingMachiningRunIds) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PartCount(
      String status,
      BigDecimal netIncrease,
      int usedTransitionCount,
      int resetCount,
      int unavailableObservationCount,
      String reason,
      List<Association> associations) {}
}
