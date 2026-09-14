package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ToolLoadTrendReport(
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

  public record MachiningRunInput(
      String machiningRunId,
      String programName,
      String status,
      Instant startedAt,
      Instant endedAt) {}

  public record ToolNumberObservation(
      boolean available, Integer value, long replaySequence, Instant sourceObservedAt) {}

  public record LoadObservation(
      boolean available,
      BigDecimal value,
      String unit,
      String componentId,
      long replaySequence,
      Instant sourceObservedAt,
      String sourceEventKey,
      String rawRecordId,
      String sourceDataItemId,
      String mappingVersion,
      String sourceKind,
      String provider,
      String sourceSetId) {}

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
