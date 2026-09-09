package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record CounterDelta(
    AccumulatedTimeMetric metric,
    BigDecimal valueSeconds,
    int usedTransitionCount,
    int resetCount,
    int unavailableObservationCount) {

  public CounterDelta {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(valueSeconds, "valueSeconds");
    if (valueSeconds.signum() < 0
        || usedTransitionCount < 0
        || resetCount < 0
        || unavailableObservationCount < 0) {
      throw new IllegalArgumentException("Counter delta values must not be negative");
    }
  }

  public boolean hasCoverageGap() {
    return resetCount > 0 || unavailableObservationCount > 0;
  }

  public boolean hasEnoughData() {
    return usedTransitionCount > 0;
  }
}
