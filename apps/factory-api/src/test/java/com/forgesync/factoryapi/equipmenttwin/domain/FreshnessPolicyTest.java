package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FreshnessPolicyTest {

  private static final Instant PROJECTED_AT = Instant.parse("2026-09-02T01:02:03Z");
  private final FreshnessPolicy policy =
      new FreshnessPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10));

  @Test
  void classifiesExactFreshAndLaggingBoundaries() {
    assertThat(policy.classify(PROJECTED_AT, PROJECTED_AT.plusSeconds(2)))
        .isEqualTo(FreshnessState.FRESH);
    assertThat(policy.classify(PROJECTED_AT, PROJECTED_AT.plusSeconds(2).plusNanos(1)))
        .isEqualTo(FreshnessState.LAGGING);
    assertThat(policy.classify(PROJECTED_AT, PROJECTED_AT.plusSeconds(10)))
        .isEqualTo(FreshnessState.LAGGING);
    assertThat(policy.classify(PROJECTED_AT, PROJECTED_AT.plusSeconds(10).plusNanos(1)))
        .isEqualTo(FreshnessState.STALE);
  }

  @Test
  void classifiesByProjectionTimeInsteadOfHistoricalSourceTime() {
    Instant historicalSourceTime = Instant.parse("2016-10-05T09:01:37Z");

    assertThat(historicalSourceTime).isBefore(PROJECTED_AT);
    assertThat(policy.classify(PROJECTED_AT, PROJECTED_AT)).isEqualTo(FreshnessState.FRESH);
  }

  @Test
  void rejectsInvalidWindowsAndClockRegression() {
    assertThatThrownBy(() -> new FreshnessPolicy(Duration.ofSeconds(11), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.classify(PROJECTED_AT, PROJECTED_AT.minusNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
