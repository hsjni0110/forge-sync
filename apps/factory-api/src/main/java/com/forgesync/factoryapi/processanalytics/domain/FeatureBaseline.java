package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.util.List;

public record FeatureBaseline(
    String featureKey,
    BigDecimal targetValue,
    BigDecimal median,
    BigDecimal firstQuartile,
    BigDecimal thirdQuartile,
    BigDecimal interquartileRange,
    int sampleCount,
    List<String> contributingFeatureSetIds,
    String unavailableReason) {
  public FeatureBaseline {
    contributingFeatureSetIds = List.copyOf(contributingFeatureSetIds);
  }

  public boolean isAvailable() {
    return median != null;
  }
}
