package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CycleFeatureResponse(
    String schemaVersion,
    String featureProcessingRunId,
    String machiningRunProcessingRunId,
    String machineId,
    String cycleFeatureVersion,
    String inputHash,
    int inputObservationCount,
    int eligibleRunCount,
    String resultHash,
    Instant createdAt,
    List<FeatureSetDto> featureSets) {

  public record FeatureSetDto(
      String cycleFeatureSetId,
      String machiningRunId,
      String cycleFeatureVersion,
      String status,
      AggregationWindowDto aggregationWindow,
      StateFeaturesDto stateFeatures,
      List<MetricFeatureDto> metricFeatures,
      ObservationRangeDto sourceObservationRange,
      DerivedProvenanceDto provenance,
      String resultHash) {}

  public record AggregationWindowDto(
      Instant startedAt, Instant endedAt, BigDecimal durationSeconds, String boundary) {}

  public record StateFeaturesDto(
      String calculation,
      BigDecimal cuttingSeconds,
      BigDecimal idleSeconds,
      CoverageDto coverage,
      List<ObservationProvenanceDto> provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MetricFeatureDto(
      String metric,
      String componentId,
      String sourceDataItemId,
      String unit,
      String status,
      String calculation,
      BigDecimal mean,
      BigDecimal maximum,
      BigDecimal populationStandardDeviation,
      CoverageDto coverage,
      List<String> contributingSourceEventKeys,
      List<ObservationProvenanceDto> provenance) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CoverageDto(
      BigDecimal coveredSeconds, BigDecimal windowSeconds, BigDecimal ratio) {}

  public record ObservationRangeDto(
      UUID replaySessionId,
      long firstReplaySequence,
      long lastReplaySequence,
      Instant firstSourceObservedAt,
      Instant lastSourceObservedAt,
      String firstSourceEventKey,
      String lastSourceEventKey) {}

  public record DerivedProvenanceDto(
      String origin,
      List<ObservationProvenanceDto> observations,
      FeatureTransformationDto transformation) {}

  public record ObservationProvenanceDto(
      SourceDto source, ObservationTransformationDto transformation) {}

  public record SourceDto(String kind, String provider, String sourceSetId, String artifactId) {}

  public record ObservationTransformationDto(
      String rawRecordId, String mappingVersion, String sourceDataItemId) {}

  public record FeatureTransformationDto(
      String featureProcessingRunId, String cycleFeatureVersion, String calculation) {}
}
