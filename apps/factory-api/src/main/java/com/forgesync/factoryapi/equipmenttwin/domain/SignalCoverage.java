package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How much of the observed range one signal accounts for. Interval totals are smaller than the
 * range by design (ADR-050), so the remainder is named rather than hidden: time before the signal
 * was first observed, and the tail of an interval no observation has closed.
 */
public record SignalCoverage(
    StateSignal signal,
    Duration closedDuration,
    Duration leadingUnobserved,
    Instant openSince,
    int intervalCount) {

  public SignalCoverage {
    Objects.requireNonNull(signal, "signal");
    Objects.requireNonNull(closedDuration, "closedDuration");
    Objects.requireNonNull(leadingUnobserved, "leadingUnobserved");
    if (closedDuration.isNegative() || leadingUnobserved.isNegative()) {
      throw new IllegalArgumentException("Coverage durations must not be negative");
    }
    if (intervalCount < 0) {
      throw new IllegalArgumentException("intervalCount must not be negative");
    }
  }

  public boolean hasOpenInterval() {
    return openSince != null;
  }
}
