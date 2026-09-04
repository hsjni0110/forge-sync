package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingResult;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureSet;
import com.forgesync.factoryapi.processanalytics.domain.FeatureCoverage;
import com.forgesync.factoryapi.processanalytics.domain.MetricFeature;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;

final class CycleFeatureResponseMapper {
  CycleFeatureResponse map(CycleFeatureProcessingResult result) {
    return new CycleFeatureResponse(
        "1.0.0",
        result.featureProcessingRunId(),
        result.machiningRunProcessingRunId(),
        result.machineId(),
        result.cycleFeatureVersion(),
        result.inputHash(),
        result.inputObservationCount(),
        result.eligibleRunCount(),
        result.resultHash(),
        result.createdAt(),
        result.featureSets().stream().map(set -> mapFeatureSet(result, set)).toList());
  }

  private CycleFeatureResponse.FeatureSetDto mapFeatureSet(
      CycleFeatureProcessingResult result, CycleFeatureSet set) {
    var feature = set.cycleFeature();
    return new CycleFeatureResponse.FeatureSetDto(
        set.cycleFeatureSetId(),
        feature.machiningRunId(),
        feature.cycleFeatureVersion(),
        feature.status().name(),
        new CycleFeatureResponse.AggregationWindowDto(
            feature.startedAt(),
            feature.endedAt(),
            feature.durationSeconds(),
            "[STARTED_AT,ENDED_AT)"),
        new CycleFeatureResponse.StateFeaturesDto(
            "EXECUTION_LAST_OBSERVATION_CARRIED_FORWARD",
            feature.cuttingSeconds(),
            feature.idleSeconds(),
            mapCoverage(feature.stateCoverage()),
            feature.stateContributingProvenance().stream().map(this::mapProvenance).toList()),
        feature.metricFeatures().stream().map(this::mapMetric).toList(),
        mapRange(feature.sourceObservationRange()),
        new CycleFeatureResponse.DerivedProvenanceDto(
            "DERIVED",
            feature.contributingProvenance().stream().map(this::mapProvenance).toList(),
            new CycleFeatureResponse.FeatureTransformationDto(
                result.featureProcessingRunId(),
                result.cycleFeatureVersion(),
                "TIME_WEIGHTED_LAST_OBSERVATION_CARRIED_FORWARD")),
        feature.resultHash());
  }

  private CycleFeatureResponse.MetricFeatureDto mapMetric(MetricFeature feature) {
    return new CycleFeatureResponse.MetricFeatureDto(
        feature.metric().name(),
        feature.componentId(),
        feature.sourceDataItemId(),
        feature.unit(),
        feature.status().name(),
        feature.calculation(),
        feature.mean(),
        feature.maximum(),
        feature.populationStandardDeviation(),
        mapCoverage(feature.coverage()),
        feature.contributingSourceEventKeys(),
        feature.contributingProvenance().stream().map(this::mapProvenance).toList());
  }

  private static CycleFeatureResponse.CoverageDto mapCoverage(FeatureCoverage coverage) {
    return new CycleFeatureResponse.CoverageDto(
        coverage.coveredSeconds(), coverage.windowSeconds(), coverage.ratio());
  }

  private static CycleFeatureResponse.ObservationRangeDto mapRange(ObservationRange range) {
    return new CycleFeatureResponse.ObservationRangeDto(
        range.replaySessionId(),
        range.firstReplaySequence(),
        range.lastReplaySequence(),
        range.firstSourceObservedAt(),
        range.lastSourceObservedAt(),
        range.firstSourceEventKey(),
        range.lastSourceEventKey());
  }

  private CycleFeatureResponse.ObservationProvenanceDto mapProvenance(
      ObservationProvenance provenance) {
    return new CycleFeatureResponse.ObservationProvenanceDto(
        new CycleFeatureResponse.SourceDto(
            provenance.sourceKind(),
            provenance.provider(),
            provenance.sourceSetId(),
            provenance.artifactId()),
        new CycleFeatureResponse.ObservationTransformationDto(
            provenance.rawRecordId(), provenance.mappingVersion(), provenance.sourceDataItemId()));
  }
}
