package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EquipmentStateIntervalResponse(
    String schemaVersion,
    String processingRunId,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String intervalRuleVersion,
    String observedFrom,
    String observedTo,
    String inputHash,
    int inputObservationCount,
    String resultHash,
    String createdAt,
    List<Coverage> coverage,
    List<Interval> intervals) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Coverage(
      String signal,
      String closedDuration,
      String leadingUnobserved,
      String openSince,
      int intervalCount) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Interval(
      String signal,
      String value,
      String startedAt,
      String endedAt,
      Double durationSeconds,
      Evidence startEvidence,
      Evidence endEvidence) {}

  public record Evidence(long replaySequence, String sourceObservedAt, String sourceEventKey) {}
}
