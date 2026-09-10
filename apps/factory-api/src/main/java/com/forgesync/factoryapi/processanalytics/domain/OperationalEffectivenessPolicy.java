package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class OperationalEffectivenessPolicy {
  public static final String POLICY_VERSION = "1.0.0";

  public PerformanceComponent performance(
      String targetFeatureSetId,
      String programName,
      BigDecimal actualCycleSeconds,
      List<CyclePerformanceSample> earlierSamples,
      BigDecimal assumedIdealCycleSeconds) {
    if (actualCycleSeconds == null || actualCycleSeconds.signum() <= 0) {
      return unavailablePerformance(actualCycleSeconds, 0, List.of(), "ACTUAL_CYCLE_UNAVAILABLE");
    }
    if (assumedIdealCycleSeconds != null) {
      if (assumedIdealCycleSeconds.signum() <= 0) {
        throw new IllegalArgumentException("Assumed ideal cycle seconds must be positive");
      }
      return availablePerformance(
          actualCycleSeconds,
          assumedIdealCycleSeconds,
          "ASSUMED_IDEAL_CYCLE",
          ValueProvenance.ASSUMED,
          List.of());
    }
    List<CyclePerformanceSample> allEligible =
        earlierSamples.stream()
            .filter(sample -> programName != null && programName.equals(sample.programName()))
            .filter(
                sample -> sample.durationSeconds() != null && sample.durationSeconds().signum() > 0)
            .sorted(
                Comparator.comparing(CyclePerformanceSample::startedAt)
                    .thenComparing(CyclePerformanceSample::featureSetId))
            .toList();
    List<CyclePerformanceSample> eligible =
        allEligible.stream()
            .skip(Math.max(0, allEligible.size() - CycleBaselinePolicy.MAXIMUM_SAMPLE_COUNT))
            .toList();
    List<String> contributors =
        eligible.stream().map(CyclePerformanceSample::featureSetId).toList();
    if (eligible.size() < CycleBaselinePolicy.MINIMUM_SAMPLE_COUNT) {
      return unavailablePerformance(
          actualCycleSeconds, eligible.size(), contributors, "MINIMUM_SAMPLE_COUNT_NOT_MET");
    }
    List<BigDecimal> sorted =
        eligible.stream().map(CyclePerformanceSample::durationSeconds).sorted().toList();
    BigDecimal reference = median(sorted);
    return availablePerformance(
        actualCycleSeconds, reference, "HISTORICAL_MEDIAN", ValueProvenance.DERIVED, contributors);
  }

  public ThroughputComponent throughput(List<PartCountObservation> observations) {
    List<PartCountObservation> ordered =
        observations.stream()
            .sorted(
                Comparator.comparingLong(PartCountObservation::replaySequence)
                    .thenComparing(PartCountObservation::sourceObservedAt)
                    .thenComparing(PartCountObservation::sourceEventKey))
            .toList();
    BigDecimal count = BigDecimal.ZERO;
    int used = 0;
    int resets = 0;
    int unavailable = 0;
    PartCountObservation previous = null;
    for (PartCountObservation current : ordered) {
      if (!current.isAvailable()) {
        unavailable++;
        previous = null;
        continue;
      }
      if (previous != null) {
        BigDecimal delta = current.value().subtract(previous.value());
        if (delta.signum() < 0) resets++;
        else {
          count = count.add(delta);
          used++;
        }
      }
      previous = current;
    }
    if (used == 0) {
      return new ThroughputComponent(
          ComponentStatus.UNAVAILABLE, null, 0, resets, unavailable, "NO_USABLE_TRANSITIONS");
    }
    return new ThroughputComponent(
        ComponentStatus.AVAILABLE, count.stripTrailingZeros(), used, resets, unavailable, null);
  }

  public UnavailableComponent unavailableQuality() {
    return new UnavailableComponent(
        ComponentStatus.UNAVAILABLE,
        null,
        ValueProvenance.UNAVAILABLE,
        "QUALITY_SOURCE_NOT_AVAILABLE");
  }

  public UnavailableComponent unavailableComposite() {
    return new UnavailableComponent(
        ComponentStatus.UNAVAILABLE,
        null,
        ValueProvenance.UNAVAILABLE,
        "QUALITY_COMPONENT_UNAVAILABLE");
  }

  private static PerformanceComponent availablePerformance(
      BigDecimal actual,
      BigDecimal reference,
      String referenceKind,
      ValueProvenance provenance,
      List<String> contributors) {
    BigDecimal percent =
        reference.multiply(BigDecimal.valueOf(100)).divide(actual, 6, RoundingMode.HALF_UP);
    return new PerformanceComponent(
        ComponentStatus.AVAILABLE,
        percent,
        normalized(actual),
        normalized(reference),
        referenceKind,
        provenance,
        contributors.size(),
        List.copyOf(contributors),
        null);
  }

  private static PerformanceComponent unavailablePerformance(
      BigDecimal actual, int sampleCount, List<String> contributors, String reason) {
    return new PerformanceComponent(
        ComponentStatus.UNAVAILABLE,
        null,
        actual == null ? null : normalized(actual),
        null,
        "HISTORICAL_MEDIAN",
        ValueProvenance.UNAVAILABLE,
        sampleCount,
        List.copyOf(contributors),
        reason);
  }

  private static BigDecimal median(List<BigDecimal> values) {
    int middle = values.size() / 2;
    BigDecimal value =
        values.size() % 2 == 1
            ? values.get(middle)
            : values.get(middle - 1).add(values.get(middle)).divide(BigDecimal.valueOf(2));
    return normalized(value);
  }

  private static BigDecimal normalized(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP);
  }
}
