package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.forgesync.factoryapi.processanalytics.application.CycleFeatureSet;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeature;
import com.forgesync.factoryapi.processanalytics.domain.CycleMetric;
import com.forgesync.factoryapi.processanalytics.domain.FeatureAvailability;
import com.forgesync.factoryapi.processanalytics.domain.FeatureCoverage;
import com.forgesync.factoryapi.processanalytics.domain.MetricFeature;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CycleFeatureDocument(
    String cycleFeatureSetId,
    String cycleFeatureVersion,
    String machiningRunId,
    Instant startedAt,
    Instant endedAt,
    String status,
    BigDecimal durationSeconds,
    BigDecimal cuttingSeconds,
    BigDecimal idleSeconds,
    CoverageDocument stateCoverage,
    List<ProvenanceDocument> stateContributingProvenance,
    List<MetricDocument> metricFeatures,
    RangeDocument sourceObservationRange,
    List<ProvenanceDocument> contributingProvenance,
    String resultHash) {

  static CycleFeatureDocument from(CycleFeatureSet set) {
    CycleFeature feature = set.cycleFeature();
    return new CycleFeatureDocument(
        set.cycleFeatureSetId(),
        feature.cycleFeatureVersion(),
        feature.machiningRunId(),
        feature.startedAt(),
        feature.endedAt(),
        feature.status().name(),
        feature.durationSeconds(),
        feature.cuttingSeconds(),
        feature.idleSeconds(),
        CoverageDocument.from(feature.stateCoverage()),
        feature.stateContributingProvenance().stream().map(ProvenanceDocument::from).toList(),
        feature.metricFeatures().stream().map(MetricDocument::from).toList(),
        RangeDocument.from(feature.sourceObservationRange()),
        feature.contributingProvenance().stream().map(ProvenanceDocument::from).toList(),
        feature.resultHash());
  }

  CycleFeatureSet toDomain() {
    return new CycleFeatureSet(
        cycleFeatureSetId,
        new CycleFeature(
            cycleFeatureVersion,
            machiningRunId,
            startedAt,
            endedAt,
            FeatureAvailability.valueOf(status),
            durationSeconds,
            cuttingSeconds,
            idleSeconds,
            stateCoverage.toDomain(),
            stateContributingProvenance.stream().map(ProvenanceDocument::toDomain).toList(),
            metricFeatures.stream().map(MetricDocument::toDomain).toList(),
            sourceObservationRange.toDomain(),
            contributingProvenance.stream().map(ProvenanceDocument::toDomain).toList(),
            resultHash));
  }

  record CoverageDocument(BigDecimal coveredSeconds, BigDecimal windowSeconds, BigDecimal ratio) {
    static CoverageDocument from(FeatureCoverage coverage) {
      return new CoverageDocument(
          coverage.coveredSeconds(), coverage.windowSeconds(), coverage.ratio());
    }

    FeatureCoverage toDomain() {
      return new FeatureCoverage(coveredSeconds, windowSeconds, ratio);
    }
  }

  record MetricDocument(
      String metric,
      String componentId,
      String sourceDataItemId,
      String unit,
      String status,
      String calculation,
      BigDecimal mean,
      BigDecimal maximum,
      BigDecimal populationStandardDeviation,
      CoverageDocument coverage,
      List<String> contributingSourceEventKeys,
      List<ProvenanceDocument> contributingProvenance) {
    static MetricDocument from(MetricFeature feature) {
      return new MetricDocument(
          feature.metric().name(),
          feature.componentId(),
          feature.sourceDataItemId(),
          feature.unit(),
          feature.status().name(),
          feature.calculation(),
          feature.mean(),
          feature.maximum(),
          feature.populationStandardDeviation(),
          CoverageDocument.from(feature.coverage()),
          feature.contributingSourceEventKeys(),
          feature.contributingProvenance().stream().map(ProvenanceDocument::from).toList());
    }

    MetricFeature toDomain() {
      return new MetricFeature(
          CycleMetric.valueOf(metric),
          componentId,
          sourceDataItemId,
          unit,
          FeatureAvailability.valueOf(status),
          calculation,
          mean,
          maximum,
          populationStandardDeviation,
          coverage.toDomain(),
          contributingSourceEventKeys,
          contributingProvenance.stream().map(ProvenanceDocument::toDomain).toList());
    }
  }

  record RangeDocument(
      UUID replaySessionId,
      long firstReplaySequence,
      long lastReplaySequence,
      Instant firstSourceObservedAt,
      Instant lastSourceObservedAt,
      String firstSourceEventKey,
      String lastSourceEventKey) {
    static RangeDocument from(ObservationRange range) {
      return new RangeDocument(
          range.replaySessionId(),
          range.firstReplaySequence(),
          range.lastReplaySequence(),
          range.firstSourceObservedAt(),
          range.lastSourceObservedAt(),
          range.firstSourceEventKey(),
          range.lastSourceEventKey());
    }

    ObservationRange toDomain() {
      return new ObservationRange(
          replaySessionId,
          firstReplaySequence,
          lastReplaySequence,
          firstSourceObservedAt,
          lastSourceObservedAt,
          firstSourceEventKey,
          lastSourceEventKey);
    }
  }

  record ProvenanceDocument(
      String sourceKind,
      String provider,
      String sourceSetId,
      String artifactId,
      String rawRecordId,
      String mappingVersion,
      String sourceDataItemId) {
    static ProvenanceDocument from(ObservationProvenance provenance) {
      return new ProvenanceDocument(
          provenance.sourceKind(),
          provenance.provider(),
          provenance.sourceSetId(),
          provenance.artifactId(),
          provenance.rawRecordId(),
          provenance.mappingVersion(),
          provenance.sourceDataItemId());
    }

    ObservationProvenance toDomain() {
      return new ObservationProvenance(
          sourceKind,
          provider,
          sourceSetId,
          artifactId,
          rawRecordId,
          mappingVersion,
          sourceDataItemId);
    }
  }
}
