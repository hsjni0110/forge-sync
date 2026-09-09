package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A comparison of two evidence paths, not a claim that their meanings are identical. */
public record UtilizationComparison(
    KpiDataStatus status,
    BigDecimal intervalActivePercent,
    BigDecimal counterAutomaticPercent,
    BigDecimal counterAutomaticMinusIntervalActivePercentagePoints,
    KpiUnavailableReason reason) {

  public UtilizationComparison {
    Objects.requireNonNull(status, "status");
    if (status == KpiDataStatus.UNAVAILABLE
        && (counterAutomaticPercent != null
            || counterAutomaticMinusIntervalActivePercentagePoints != null
            || reason == null)) {
      throw new IllegalArgumentException(
          "Unavailable comparisons require a reason and no counter value");
    }
    if (status != KpiDataStatus.UNAVAILABLE
        && (counterAutomaticPercent == null
            || counterAutomaticMinusIntervalActivePercentagePoints == null
            || reason != null)) {
      throw new IllegalArgumentException("Available comparisons require both values");
    }
  }
}
