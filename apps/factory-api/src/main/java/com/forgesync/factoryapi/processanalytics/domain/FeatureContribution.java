package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.util.List;

public record FeatureContribution(
    String featureKey,
    BigDecimal targetValue,
    BigDecimal baselineMedian,
    BigDecimal difference,
    BigDecimal percentageDifference,
    String direction,
    BigDecimal distance,
    BigDecimal score,
    int sampleCount,
    List<String> contributingFeatureSetIds,
    String reasonCode) {
  public FeatureContribution {
    contributingFeatureSetIds = List.copyOf(contributingFeatureSetIds);
  }
}
