package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UtilizationKpiPolicyTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void measuresExecutionStatesAgainstTheWholeObservedRangeWithoutHidingUnknownOrUncoveredTime() {
    EquipmentStateIntervalReport intervals = intervalReport();

    StateUtilization report = new UtilizationKpiPolicy().calculateStateUtilization(intervals);

    assertThat(report.denominatorDuration()).isEqualTo(Duration.ofSeconds(600));
    assertThat(report.durationOf(UtilizationState.ACTIVE)).isEqualTo(Duration.ofSeconds(180));
    assertThat(report.durationOf(UtilizationState.INTERRUPTED)).isEqualTo(Duration.ofSeconds(60));
    assertThat(report.durationOf(UtilizationState.UNKNOWN)).isEqualTo(Duration.ofSeconds(120));
    assertThat(report.uncoveredDuration()).isEqualTo(Duration.ofSeconds(120));
    assertThat(report.ratioPercentOf(UtilizationState.ACTIVE).toString()).isEqualTo("30.000000");
  }

  @Test
  void sumsOnlyMonotonicCounterSegmentsAndMarksResetAndUnavailableCoverageAsPartial() {
    CounterUtilization counters =
        new UtilizationKpiPolicy()
            .calculateCounterUtilization(
                List.of(
                    counter("TOTAL", 0, 0, "100"),
                    counter("AUTO", 0, 0, "30"),
                    counter("CUT", 0, 0, "10"),
                    counter("TOTAL", 1, 10, "200"),
                    counter("AUTO", 1, 10, "80"),
                    counter("CUT", 1, 10, "30"),
                    counter("TOTAL", 2, 20, "50"),
                    counter("AUTO", 2, 20, "10"),
                    unavailableCounter("CUT", 2, 20),
                    counter("TOTAL", 3, 30, "150"),
                    counter("AUTO", 3, 30, "60"),
                    counter("CUT", 3, 30, "5"),
                    counter("CUT", 4, 40, "25")));

    assertThat(counters.automaticRatio().status()).isEqualTo(KpiDataStatus.PARTIAL);
    assertThat(counters.automaticRatio().ratioPercent().toString()).isEqualTo("50.000000");
    assertThat(counters.cuttingRatio().status()).isEqualTo(KpiDataStatus.PARTIAL);
    assertThat(counters.cuttingRatio().ratioPercent().toString()).isEqualTo("40.000000");
    assertThat(counters.totalDelta().resetCount()).isEqualTo(1);
  }

  @Test
  void makesARatioUnavailableInsteadOfClampingWhenTheNumeratorExceedsTheDenominator() {
    CounterUtilization counters =
        new UtilizationKpiPolicy()
            .calculateCounterUtilization(
                List.of(
                    counter("TOTAL", 0, 0, "100"),
                    counter("AUTO", 0, 0, "20"),
                    counter("TOTAL", 1, 10, "150"),
                    counter("AUTO", 1, 10, "80")));

    CounterRatio automatic = counters.automaticRatio();
    assertThat(automatic.status()).isEqualTo(KpiDataStatus.UNAVAILABLE);
    assertThat(automatic.reason()).isEqualTo(KpiUnavailableReason.COUNTER_RELATION_VIOLATION);
    assertThat(automatic.ratioPercent()).isNull();
  }

  @Test
  void exposesBothCalculationPathsAndTheirSignedPercentagePointDifference() {
    List<AccumulatedTimeObservation> counters =
        List.of(
            counter("TOTAL", 0, 0, "100"),
            counter("AUTO", 0, 0, "20"),
            counter("CUT", 0, 0, "5"),
            counter("TOTAL", 1, 600, "300"),
            counter("AUTO", 1, 600, "120"),
            counter("CUT", 1, 600, "45"));

    UtilizationKpiReport report =
        new UtilizationKpiPolicy()
            .calculate("sha256:" + "a".repeat(64), intervalReport(), counters);
    UtilizationComparison comparison = report.comparison();

    assertThat(comparison.intervalActivePercent().toString()).isEqualTo("30.000000");
    assertThat(comparison.counterAutomaticPercent().toString()).isEqualTo("50.000000");
    assertThat(comparison.counterAutomaticMinusIntervalActivePercentagePoints().toString())
        .isEqualTo("20.000000");
  }

  @Test
  void makesStateRatiosUnavailableWhenTheObservedRangeHasNoDuration()
      throws ReflectiveOperationException {
    List<StateSignalObservation> observations = List.of(available(0, 0, "ACTIVE"));
    EquipmentStateIntervalPolicy intervalPolicy = new EquipmentStateIntervalPolicy();
    EquipmentStateIntervalReport intervals =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations,
            intervalPolicy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));

    StateUtilization state = new UtilizationKpiPolicy().calculateStateUtilization(intervals);

    assertThat(state.getClass().getMethod("status").invoke(state).toString())
        .isEqualTo("UNAVAILABLE");
    assertThat(state.getClass().getMethod("reason").invoke(state).toString())
        .isEqualTo("ZERO_DENOMINATOR");
    assertThat(state.ratioPercentOf(UtilizationState.ACTIVE)).isNull();
  }

  @Test
  void rejectsCountersFromMoreThanOneReplaySession() {
    AccumulatedTimeObservation first = counter("TOTAL", 0, 0, "100");
    AccumulatedTimeObservation otherSession =
        new AccumulatedTimeObservation(
            "Mazak01",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            1,
            START.plusSeconds(1),
            "other-session",
            AccumulatedTimeMetric.TOTAL,
            true,
            new BigDecimal("101"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new UtilizationKpiPolicy()
                    .calculateCounterUtilization(List.of(first, otherSession)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Replay Session");
  }

  private static EquipmentStateIntervalReport intervalReport() {
    List<StateSignalObservation> observations =
        List.of(
            available(0, 0, "READY"),
            available(1, 60, "ACTIVE"),
            available(2, 240, "FEED_HOLD"),
            unavailable(3, 300),
            available(4, 420, "STOPPED"),
            available(5, 480, "READY"),
            mode(6, 600, "AUTOMATIC"));
    EquipmentStateIntervalPolicy intervalPolicy = new EquipmentStateIntervalPolicy();
    return EquipmentStateIntervalReport.of(
        EquipmentStateIntervalPolicy.RULE_VERSION,
        observations,
        intervalPolicy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
  }

  private static StateSignalObservation available(long sequence, long seconds, String value) {
    return new StateSignalObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "event-" + sequence,
        StateSignal.EXECUTION,
        true,
        value);
  }

  private static StateSignalObservation unavailable(long sequence, long seconds) {
    return new StateSignalObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "event-" + sequence,
        StateSignal.EXECUTION,
        false,
        null);
  }

  private static StateSignalObservation mode(long sequence, long seconds, String value) {
    return new StateSignalObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "event-" + sequence,
        StateSignal.CONTROLLER_MODE,
        true,
        value);
  }

  private static AccumulatedTimeObservation counter(
      String metric, long sequence, long seconds, String value) {
    return newCounter(metric, sequence, seconds, true, new BigDecimal(value));
  }

  private static AccumulatedTimeObservation unavailableCounter(
      String metric, long sequence, long seconds) {
    return newCounter(metric, sequence, seconds, false, null);
  }

  private static AccumulatedTimeObservation newCounter(
      String metric, long sequence, long seconds, boolean isAvailable, BigDecimal value) {
    return new AccumulatedTimeObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "counter-" + metric + "-" + sequence,
        AccumulatedTimeMetric.valueOf(metric),
        isAvailable,
        value);
  }
}
