package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UtilizationKpiResponse(
    String schemaVersion,
    String processingRunId,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String calculationVersion,
    String intervalProcessingRunId,
    String observedFrom,
    String observedTo,
    String inputHash,
    String resultHash,
    String createdAt,
    State state,
    Counters counters,
    Comparison comparison) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record State(
      String status,
      String reason,
      String source,
      String valueProvenance,
      String denominatorDuration,
      String uncoveredDuration,
      String formula,
      List<StateDuration> states) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StateDuration(String state, String duration, BigDecimal ratioPercent) {}

  public record Counters(
      String source,
      String valueProvenance,
      List<CounterDelta> deltas,
      Ratio automaticRatio,
      Ratio cuttingRatio) {}

  public record CounterDelta(
      String metric,
      BigDecimal valueSeconds,
      String unit,
      String unitProvenance,
      int usedTransitionCount,
      int resetCount,
      int unavailableObservationCount) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Ratio(String status, BigDecimal ratioPercent, String reason, String formula) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Comparison(
      String status,
      BigDecimal intervalActivePercent,
      BigDecimal counterAutomaticPercent,
      BigDecimal counterAutomaticMinusIntervalActivePercentagePoints,
      String reason,
      String interpretation) {}
}
