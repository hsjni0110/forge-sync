package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnomalyAssessmentResponse(
    String schemaVersion,
    String assessmentProcessingRunId,
    String cycleFeatureProcessingRunId,
    String machiningRunProcessingRunId,
    String machineId,
    String cycleFeatureVersion,
    String baselinePolicyVersion,
    String anomalyAssessmentVersion,
    String inputHash,
    String resultHash,
    Instant createdAt,
    List<AssessmentDto> assessments) {
  public record AssessmentDto(
      String assessmentId,
      String machiningRunId,
      String targetFeatureSetId,
      String dataStatus,
      String classification,
      BigDecimal score,
      BaselineDto baseline,
      ObservationRangeDto evaluationSourceRange,
      List<ContributionDto> contributions,
      List<ContributionDto> topReasons,
      LineageDto lineage,
      String resultHash) {}

  public record BaselineDto(
      String baselineGroupId,
      String machineId,
      String programName,
      String cycleFeatureVersion,
      String baselinePolicyVersion,
      String targetFeatureSetId,
      List<String> candidateFeatureSetIds,
      Instant trainingStartedAt,
      Instant trainingEndedAt,
      List<ObservationRangeDto> trainingSourceRanges,
      List<FeatureBaselineDto> featureBaselines) {}

  public record FeatureBaselineDto(
      String featureKey,
      BigDecimal targetValue,
      BigDecimal median,
      BigDecimal firstQuartile,
      BigDecimal thirdQuartile,
      BigDecimal interquartileRange,
      int sampleCount,
      List<String> contributingFeatureSetIds,
      String unavailableReason,
      boolean available) {}

  public record ContributionDto(
      String featureKey,
      BigDecimal targetValue,
      BigDecimal baselineMedian,
      BigDecimal difference,
      BigDecimal percentageDifference,
      String direction,
      BigDecimal distance,
      BigDecimal deviationScale,
      BigDecimal score,
      int sampleCount,
      List<String> contributingFeatureSetIds,
      String reasonCode) {}

  public record ObservationRangeDto(
      UUID replaySessionId,
      long firstReplaySequence,
      long lastReplaySequence,
      Instant firstSourceObservedAt,
      Instant lastSourceObservedAt,
      String firstSourceEventKey,
      String lastSourceEventKey) {}

  public record LineageDto(
      String origin, DerivedCycleFeatureDto inputCycleFeature, List<SourceLineageDto> sources) {}

  public record DerivedCycleFeatureDto(
      String origin, String featureProcessingRunId, String cycleFeatureVersion) {}

  public record SourceLineageDto(
      String kind,
      String provider,
      String sourceSetId,
      String artifactId,
      String rawRecordId,
      String mappingVersion,
      String sourceDataItemId) {}
}
