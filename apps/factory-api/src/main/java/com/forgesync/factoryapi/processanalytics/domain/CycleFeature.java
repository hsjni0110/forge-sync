package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CycleFeature(
    String cycleFeatureVersion,
    String machiningRunId,
    Instant startedAt,
    Instant endedAt,
    FeatureAvailability status,
    BigDecimal durationSeconds,
    BigDecimal cuttingSeconds,
    BigDecimal idleSeconds,
    FeatureCoverage stateCoverage,
    List<ObservationProvenance> stateContributingProvenance,
    List<MetricFeature> metricFeatures,
    ObservationRange sourceObservationRange,
    List<ObservationProvenance> contributingProvenance,
    String resultHash) {
  public CycleFeature {
    stateContributingProvenance = List.copyOf(stateContributingProvenance);
    metricFeatures = List.copyOf(metricFeatures);
    contributingProvenance = List.copyOf(contributingProvenance);
  }
}
