package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FreshnessPolicy {

  private final Duration freshMaxAge;
  private final Duration laggingMaxAge;

  public FreshnessPolicy(Duration freshMaxAge, Duration laggingMaxAge) {
    this.freshMaxAge = requireNonNegative(freshMaxAge, "freshMaxAge");
    this.laggingMaxAge = requireNonNegative(laggingMaxAge, "laggingMaxAge");
    if (freshMaxAge.compareTo(laggingMaxAge) > 0) {
      throw new IllegalArgumentException("freshMaxAge must not exceed laggingMaxAge");
    }
  }

  public FreshnessState classify(Instant projectedAt, Instant evaluatedAt) {
    Objects.requireNonNull(projectedAt, "projectedAt");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    Duration age = Duration.between(projectedAt, evaluatedAt);
    if (age.isNegative()) {
      throw new IllegalArgumentException("evaluatedAt must not precede projectedAt");
    }
    if (age.compareTo(freshMaxAge) <= 0) {
      return FreshnessState.FRESH;
    }
    if (age.compareTo(laggingMaxAge) <= 0) {
      return FreshnessState.LAGGING;
    }
    return FreshnessState.STALE;
  }

  public long freshMaxAgeMillis() {
    return freshMaxAge.toMillis();
  }

  public long laggingMaxAgeMillis() {
    return laggingMaxAge.toMillis();
  }

  private static Duration requireNonNegative(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isNegative()) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    if (!duration.equals(Duration.ofMillis(duration.toMillis()))) {
      throw new IllegalArgumentException(name + " must use millisecond precision");
    }
    return duration;
  }
}
