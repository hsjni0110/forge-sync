package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record StateUtilization(
    KpiDataStatus status,
    KpiUnavailableReason reason,
    Duration denominatorDuration,
    Duration uncoveredDuration,
    Map<UtilizationState, Duration> durations) {

  public StateUtilization {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(denominatorDuration, "denominatorDuration");
    Objects.requireNonNull(uncoveredDuration, "uncoveredDuration");
    durations = Map.copyOf(durations);
    if (denominatorDuration.isNegative() || uncoveredDuration.isNegative()) {
      throw new IllegalArgumentException("Utilization durations must not be negative");
    }
    if ((status == KpiDataStatus.UNAVAILABLE) != (reason != null)) {
      throw new IllegalArgumentException("Only unavailable state utilization has a reason");
    }
  }

  public Duration durationOf(UtilizationState state) {
    return durations.getOrDefault(state, Duration.ZERO);
  }

  public BigDecimal ratioPercentOf(UtilizationState state) {
    if (denominatorDuration.isZero()) {
      return null;
    }
    return decimalSeconds(durationOf(state))
        .multiply(BigDecimal.valueOf(100))
        .divide(decimalSeconds(denominatorDuration), 6, RoundingMode.HALF_UP);
  }

  static EnumMap<UtilizationState, Duration> emptyDurations() {
    EnumMap<UtilizationState, Duration> durations = new EnumMap<>(UtilizationState.class);
    for (UtilizationState state : UtilizationState.values()) {
      durations.put(state, Duration.ZERO);
    }
    return durations;
  }

  private static BigDecimal decimalSeconds(Duration duration) {
    return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
  }
}
