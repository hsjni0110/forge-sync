package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.util.List;

public record MetricFeature(
    CycleMetric metric,
    String componentId,
    String sourceDataItemId,
    String unit,
    FeatureAvailability status,
    String calculation,
    BigDecimal mean,
    BigDecimal maximum,
    BigDecimal populationStandardDeviation,
    FeatureCoverage coverage,
    List<String> contributingSourceEventKeys,
    List<ObservationProvenance> contributingProvenance) {
  public MetricFeature {
    contributingSourceEventKeys = List.copyOf(contributingSourceEventKeys);
    contributingProvenance = List.copyOf(contributingProvenance);
  }
}
