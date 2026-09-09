package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Calculates range-scoped utilization without closing intervals or filling unobserved time. */
public final class UtilizationKpiPolicy {

  public static final String RULE_VERSION = "1.0.0";

  public StateUtilization calculateStateUtilization(EquipmentStateIntervalReport report) {
    EnumMap<UtilizationState, Duration> durations = StateUtilization.emptyDurations();
    Duration measured = Duration.ZERO;
    for (EquipmentStateInterval interval : report.intervals()) {
      if (interval.signal() != StateSignal.EXECUTION || interval.isOpen()) {
        continue;
      }
      Duration duration = interval.duration().orElseThrow();
      UtilizationState state = utilizationStateOf(interval);
      durations.put(state, durations.get(state).plus(duration));
      measured = measured.plus(duration);
    }
    Duration denominator = report.observedRange();
    Duration uncovered = denominator.minus(measured);
    if (uncovered.isNegative()) {
      throw new IllegalArgumentException("Execution intervals exceed the observed range");
    }
    if (denominator.isZero()) {
      return new StateUtilization(
          KpiDataStatus.UNAVAILABLE,
          KpiUnavailableReason.ZERO_DENOMINATOR,
          denominator,
          uncovered,
          durations);
    }
    KpiDataStatus status = uncovered.isZero() ? KpiDataStatus.AVAILABLE : KpiDataStatus.PARTIAL;
    return new StateUtilization(status, null, denominator, uncovered, durations);
  }

  public CounterUtilization calculateCounterUtilization(
      List<AccumulatedTimeObservation> observations) {
    requireOneSource(observations);
    Map<AccumulatedTimeMetric, CounterDelta> deltas = calculateDeltas(observations);
    CounterDelta total = deltas.get(AccumulatedTimeMetric.TOTAL);
    CounterDelta automatic = deltas.get(AccumulatedTimeMetric.AUTO);
    CounterDelta cutting = deltas.get(AccumulatedTimeMetric.CUT);
    return new CounterUtilization(
        total, automatic, cutting, ratioOf(automatic, total), ratioOf(cutting, automatic));
  }

  public UtilizationKpiReport calculate(
      String intervalProcessingRunId,
      EquipmentStateIntervalReport intervalReport,
      List<AccumulatedTimeObservation> counterObservations) {
    StateUtilization state = calculateStateUtilization(intervalReport);
    CounterUtilization counters = calculateCounterUtilization(counterObservations);
    UtilizationComparison comparison = compare(state, counters.automaticRatio());
    String inputHash =
        EquipmentStateIntervalReport.sha256(
            RULE_VERSION
                + "\n"
                + intervalProcessingRunId
                + "\n"
                + canonicalCounterInput(counterObservations));
    String resultHash =
        EquipmentStateIntervalReport.sha256(canonicalResult(state, counters, comparison));
    return new UtilizationKpiReport(
        RULE_VERSION,
        intervalProcessingRunId,
        intervalReport.machineId(),
        intervalReport.replaySessionId(),
        intervalReport.throughReplaySequence(),
        intervalReport.observedFrom(),
        intervalReport.observedTo(),
        inputHash,
        resultHash,
        state,
        counters,
        comparison);
  }

  private static UtilizationComparison compare(
      StateUtilization state, CounterRatio automaticRatio) {
    BigDecimal intervalActive = state.ratioPercentOf(UtilizationState.ACTIVE);
    if (state.status() == KpiDataStatus.UNAVAILABLE) {
      return new UtilizationComparison(KpiDataStatus.UNAVAILABLE, null, null, null, state.reason());
    }
    if (automaticRatio.status() == KpiDataStatus.UNAVAILABLE) {
      return new UtilizationComparison(
          KpiDataStatus.UNAVAILABLE, intervalActive, null, null, automaticRatio.reason());
    }
    BigDecimal counterAutomatic = automaticRatio.ratioPercent();
    return new UtilizationComparison(
        automaticRatio.status(),
        intervalActive,
        counterAutomatic,
        counterAutomatic.subtract(intervalActive),
        null);
  }

  private static String canonicalCounterInput(List<AccumulatedTimeObservation> observations) {
    List<AccumulatedTimeObservation> ordered = new ArrayList<>(observations);
    ordered.sort(
        Comparator.comparingLong(AccumulatedTimeObservation::replaySequence)
            .thenComparing(AccumulatedTimeObservation::sourceObservedAt)
            .thenComparing(AccumulatedTimeObservation::sourceEventKey));
    StringBuilder canonical = new StringBuilder();
    for (AccumulatedTimeObservation observation : ordered) {
      canonical
          .append('\n')
          .append(observation.replaySequence())
          .append('|')
          .append(observation.sourceObservedAt())
          .append('|')
          .append(observation.sourceEventKey())
          .append('|')
          .append(observation.metric())
          .append('|')
          .append(observation.isAvailable() ? observation.valueSeconds() : "UNAVAILABLE");
    }
    return canonical.toString();
  }

  private static String canonicalResult(
      StateUtilization state, CounterUtilization counters, UtilizationComparison comparison) {
    StringBuilder canonical =
        new StringBuilder(RULE_VERSION)
            .append('|')
            .append(state.status())
            .append('|')
            .append(state.reason())
            .append('|')
            .append(state.denominatorDuration())
            .append('|')
            .append(state.uncoveredDuration());
    for (UtilizationState value : UtilizationState.values()) {
      canonical.append('\n').append(value).append('|').append(state.durationOf(value));
    }
    appendDelta(canonical, counters.totalDelta());
    appendDelta(canonical, counters.automaticDelta());
    appendDelta(canonical, counters.cuttingDelta());
    appendRatio(canonical, counters.automaticRatio());
    appendRatio(canonical, counters.cuttingRatio());
    return canonical.append('\n').append(comparison).toString();
  }

  private static void appendDelta(StringBuilder canonical, CounterDelta delta) {
    canonical
        .append('\n')
        .append(delta.metric())
        .append('|')
        .append(delta.valueSeconds())
        .append('|')
        .append(delta.usedTransitionCount())
        .append('|')
        .append(delta.resetCount())
        .append('|')
        .append(delta.unavailableObservationCount());
  }

  private static void appendRatio(StringBuilder canonical, CounterRatio ratio) {
    canonical
        .append('\n')
        .append(ratio.numerator())
        .append('/')
        .append(ratio.denominator())
        .append('|')
        .append(ratio.status())
        .append('|')
        .append(ratio.ratioPercent())
        .append('|')
        .append(ratio.reason());
  }

  private static void requireOneSource(List<AccumulatedTimeObservation> observations) {
    if (observations.isEmpty()) {
      return;
    }
    AccumulatedTimeObservation first = observations.getFirst();
    boolean mixed =
        observations.stream()
            .anyMatch(
                observation ->
                    !first.machineId().equals(observation.machineId())
                        || !first.replaySessionId().equals(observation.replaySessionId()));
    if (mixed) {
      throw new IllegalArgumentException(
          "Accumulated times cannot span more than one Machine or Replay Session");
    }
  }

  private static Map<AccumulatedTimeMetric, CounterDelta> calculateDeltas(
      List<AccumulatedTimeObservation> observations) {
    EnumMap<AccumulatedTimeMetric, List<AccumulatedTimeObservation>> grouped =
        new EnumMap<>(AccumulatedTimeMetric.class);
    for (AccumulatedTimeMetric metric : AccumulatedTimeMetric.values()) {
      grouped.put(metric, new ArrayList<>());
    }
    for (AccumulatedTimeObservation observation : observations) {
      grouped.get(observation.metric()).add(observation);
    }
    EnumMap<AccumulatedTimeMetric, CounterDelta> deltas =
        new EnumMap<>(AccumulatedTimeMetric.class);
    grouped.forEach((metric, values) -> deltas.put(metric, deltaOf(metric, values)));
    return deltas;
  }

  private static CounterDelta deltaOf(
      AccumulatedTimeMetric metric, List<AccumulatedTimeObservation> observations) {
    observations.sort(
        Comparator.comparingLong(AccumulatedTimeObservation::replaySequence)
            .thenComparing(AccumulatedTimeObservation::sourceObservedAt)
            .thenComparing(AccumulatedTimeObservation::sourceEventKey));
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal previous = null;
    int used = 0;
    int resets = 0;
    int unavailable = 0;
    for (AccumulatedTimeObservation observation : observations) {
      if (!observation.isAvailable()) {
        unavailable++;
        previous = null;
        continue;
      }
      if (previous != null) {
        BigDecimal difference = observation.valueSeconds().subtract(previous);
        if (difference.signum() < 0) {
          resets++;
        } else {
          total = total.add(difference);
          used++;
        }
      }
      previous = observation.valueSeconds();
    }
    return new CounterDelta(metric, total, used, resets, unavailable);
  }

  private static CounterRatio ratioOf(CounterDelta numerator, CounterDelta denominator) {
    if (!numerator.hasEnoughData() || !denominator.hasEnoughData()) {
      return unavailableRatio(numerator, denominator, KpiUnavailableReason.INSUFFICIENT_DATA);
    }
    if (denominator.valueSeconds().signum() == 0) {
      return unavailableRatio(numerator, denominator, KpiUnavailableReason.ZERO_DENOMINATOR);
    }
    if (numerator.valueSeconds().compareTo(denominator.valueSeconds()) > 0) {
      return unavailableRatio(
          numerator, denominator, KpiUnavailableReason.COUNTER_RELATION_VIOLATION);
    }
    BigDecimal ratio =
        numerator
            .valueSeconds()
            .multiply(BigDecimal.valueOf(100))
            .divide(denominator.valueSeconds(), 6, RoundingMode.HALF_UP);
    KpiDataStatus status =
        numerator.hasCoverageGap() || denominator.hasCoverageGap()
            ? KpiDataStatus.PARTIAL
            : KpiDataStatus.AVAILABLE;
    return new CounterRatio(numerator.metric(), denominator.metric(), status, ratio, null);
  }

  private static CounterRatio unavailableRatio(
      CounterDelta numerator, CounterDelta denominator, KpiUnavailableReason reason) {
    return new CounterRatio(
        numerator.metric(), denominator.metric(), KpiDataStatus.UNAVAILABLE, null, reason);
  }

  static UtilizationState utilizationStateOf(EquipmentStateInterval interval) {
    if (interval.isUnknown()) {
      return UtilizationState.UNKNOWN;
    }
    return switch (interval.value()) {
      case "ACTIVE" -> UtilizationState.ACTIVE;
      case "READY" -> UtilizationState.READY;
      case "STOPPED" -> UtilizationState.STOPPED;
      case "INTERRUPTED", "FEED_HOLD" -> UtilizationState.INTERRUPTED;
      default -> UtilizationState.UNKNOWN;
    };
  }
}
