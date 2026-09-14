package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ToolLoadTrendResponse(
    String schemaVersion,
    String policyVersion,
    String machineId,
    String replaySessionId,
    long throughReplaySequence,
    String machiningRunProcessingRunId,
    Policy policy,
    Provenance provenance,
    List<Group> groups) {
  public record Policy(
      int minimumRawSamplesPerPoint,
      int minimumTrendPoints,
      int baselinePointCount,
      BigDecimal minimumCoverageRatio,
      String pointFormula,
      String coverageFormula,
      String deviationFormula,
      String slopeFormula) {}

  public record Provenance(String origin, String sourceKind, String provider, String sourceSetId) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Group(
      String programName,
      int toolNumber,
      String componentId,
      String sourceDataItemId,
      String unit,
      String status,
      List<String> reasons,
      int candidatePointCount,
      int eligiblePointCount,
      BigDecimal coverageRatio,
      BigDecimal baselineMedianLoad,
      BigDecimal latestDeviationPercent,
      BigDecimal slopePercentPerPoint,
      List<Point> points) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Point(
      String machiningRunId,
      String status,
      int availableSampleCount,
      int totalObservationCount,
      BigDecimal medianLoad,
      BigDecimal deviationPercent,
      Evidence firstEvidence,
      Evidence lastEvidence) {}

  public record Evidence(
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId,
      String mappingVersion) {}
}
