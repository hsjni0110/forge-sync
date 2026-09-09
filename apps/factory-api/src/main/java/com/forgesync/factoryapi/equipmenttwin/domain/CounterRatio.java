package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record CounterRatio(
    AccumulatedTimeMetric numerator,
    AccumulatedTimeMetric denominator,
    KpiDataStatus status,
    BigDecimal ratioPercent,
    KpiUnavailableReason reason) {

  public CounterRatio {
    Objects.requireNonNull(numerator, "numerator");
    Objects.requireNonNull(denominator, "denominator");
    Objects.requireNonNull(status, "status");
    if (status == KpiDataStatus.UNAVAILABLE && (ratioPercent != null || reason == null)) {
      throw new IllegalArgumentException("Unavailable ratios require a reason and no value");
    }
    if (status != KpiDataStatus.UNAVAILABLE && (ratioPercent == null || reason != null)) {
      throw new IllegalArgumentException("Available ratios require a value and no reason");
    }
  }
}
