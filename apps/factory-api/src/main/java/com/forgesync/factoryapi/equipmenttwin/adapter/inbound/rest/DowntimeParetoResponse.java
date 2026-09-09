package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DowntimeParetoResponse(
    String schemaVersion,
    String processingRunId,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String ruleVersion,
    String utilizationProcessingRunId,
    String intervalProcessingRunId,
    String observedFrom,
    String observedTo,
    BigDecimal totalDowntimeSeconds,
    String inputHash,
    String resultHash,
    String createdAt,
    List<Entry> entries) {

  public record Entry(
      int rank,
      String state,
      String startedAt,
      String endedAt,
      BigDecimal durationSeconds,
      BigDecimal ratioPercent,
      BigDecimal cumulativeRatioPercent,
      BoundaryEvidence startEvidence,
      BoundaryEvidence endEvidence,
      String reasonClassification,
      List<Evidence> evidence) {}

  public record BoundaryEvidence(
      long replaySequence, String sourceObservedAt, String sourceEventKey) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Evidence(
      String kind,
      String signal,
      String value,
      String sourceObservedAt,
      long replaySequence,
      String sourceEventKey,
      String componentId,
      String conditionType,
      String level,
      String nativeCode,
      String message) {}
}
