package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CycleBaselinePolicy {
  public static final String POLICY_VERSION = "1.0.0";
  public static final int MINIMUM_SAMPLE_COUNT = 5;
  public static final int MAXIMUM_SAMPLE_COUNT = 30;

  public CycleBaseline build(
      String machineId, CycleFeatureContext target, List<CycleFeatureContext> earlierCandidates) {
    CycleFeature targetFeature = target.cycleFeature();
    if (target.programName() == null || target.programName().isBlank()) {
      return empty(machineId, target);
    }
    List<CycleFeatureContext> candidates =
        earlierCandidates.stream()
            .filter(candidate -> target.programName().equals(candidate.programName()))
            .filter(
                candidate ->
                    targetFeature
                        .cycleFeatureVersion()
                        .equals(candidate.cycleFeature().cycleFeatureVersion()))
            .filter(
                candidate ->
                    candidate.cycleFeature().startedAt().isBefore(targetFeature.startedAt()))
            .sorted(
                Comparator.comparing(
                        (CycleFeatureContext value) -> value.cycleFeature().startedAt())
                    .thenComparing(CycleFeatureContext::cycleFeatureSetId))
            .toList();
    Map<String, ScalarFeature> targetScalars = scalars(targetFeature);
    List<FeatureBaseline> baselines =
        targetScalars.values().stream().map(scalar -> baseline(scalar, candidates)).toList();
    String groupId =
        DeterministicHash.sha256(
            lengthPrefixed(machineId, target.programName(), targetFeature.cycleFeatureVersion()));
    Instant trainingStartedAt =
        candidates.isEmpty() ? null : candidates.getFirst().cycleFeature().startedAt();
    Instant trainingEndedAt =
        candidates.isEmpty() ? null : candidates.getLast().cycleFeature().startedAt();
    return new CycleBaseline(
        groupId,
        machineId,
        target.programName(),
        targetFeature.cycleFeatureVersion(),
        POLICY_VERSION,
        target.cycleFeatureSetId(),
        candidates.stream().map(CycleFeatureContext::cycleFeatureSetId).toList(),
        trainingStartedAt,
        trainingEndedAt,
        candidates.stream()
            .map(candidate -> candidate.cycleFeature().sourceObservationRange())
            .filter(java.util.Objects::nonNull)
            .toList(),
        baselines);
  }

  private static CycleBaseline empty(String machineId, CycleFeatureContext target) {
    return new CycleBaseline(
        DeterministicHash.sha256(
            lengthPrefixed(
                machineId, target.programName(), target.cycleFeature().cycleFeatureVersion())),
        machineId,
        target.programName(),
        target.cycleFeature().cycleFeatureVersion(),
        POLICY_VERSION,
        target.cycleFeatureSetId(),
        List.of(),
        null,
        null,
        List.of(),
        List.of());
  }

  private static FeatureBaseline baseline(
      ScalarFeature target, List<CycleFeatureContext> candidates) {
    if (target.value() == null || !target.isEligible()) {
      return new FeatureBaseline(
          target.key(),
          target.value() == null ? null : normalized(target.value()),
          null,
          null,
          null,
          null,
          0,
          List.of(),
          target.value() == null ? "TARGET_VALUE_UNAVAILABLE" : "TARGET_COVERAGE_BELOW_MINIMUM");
    }
    List<Sample> samples =
        candidates.stream()
            .map(
                candidate ->
                    new Sample(
                        candidate.cycleFeatureSetId(),
                        scalars(candidate.cycleFeature()).get(target.key())))
            .filter(
                sample ->
                    sample.feature() != null
                        && sample.feature().value() != null
                        && sample.feature().isEligible())
            .skip(Math.max(0, eligibleCount(target.key(), candidates) - MAXIMUM_SAMPLE_COUNT))
            .toList();
    if (samples.size() < MINIMUM_SAMPLE_COUNT) {
      return new FeatureBaseline(
          target.key(),
          normalized(target.value()),
          null,
          null,
          null,
          null,
          samples.size(),
          samples.stream().map(Sample::featureSetId).toList(),
          "MINIMUM_SAMPLE_COUNT_NOT_MET");
    }
    List<BigDecimal> values =
        samples.stream().map(sample -> sample.feature().value()).sorted().toList();
    BigDecimal median = median(values);
    int middle = values.size() / 2;
    List<BigDecimal> lower = values.subList(0, middle);
    List<BigDecimal> upper = values.subList((values.size() + 1) / 2, values.size());
    BigDecimal firstQuartile = median(lower);
    BigDecimal thirdQuartile = median(upper);
    return new FeatureBaseline(
        target.key(),
        normalized(target.value()),
        median,
        firstQuartile,
        thirdQuartile,
        normalized(thirdQuartile.subtract(firstQuartile)),
        samples.size(),
        samples.stream().map(Sample::featureSetId).toList(),
        null);
  }

  private static long eligibleCount(String key, List<CycleFeatureContext> candidates) {
    return candidates.stream()
        .map(candidate -> scalars(candidate.cycleFeature()).get(key))
        .filter(java.util.Objects::nonNull)
        .filter(feature -> feature.value() != null && feature.isEligible())
        .count();
  }

  private static Map<String, ScalarFeature> scalars(CycleFeature feature) {
    Map<String, ScalarFeature> values = new LinkedHashMap<>();
    add(values, "durationSeconds", feature.durationSeconds(), true);
    boolean stateCovered = covered(feature.stateCoverage());
    add(values, "cuttingSeconds", feature.cuttingSeconds(), stateCovered);
    add(values, "idleSeconds", feature.idleSeconds(), stateCovered);
    List<MetricFeature> identifiedMetrics =
        feature.metricFeatures().stream()
            .filter(
                metric ->
                    metric.componentId() != null
                        && metric.sourceDataItemId() != null
                        && metric.unit() != null)
            .sorted(Comparator.comparing(CycleBaselinePolicy::channelKey))
            .toList();
    identifiedMetrics.stream()
        .forEach(
            metric -> {
              String prefix = "metric|" + channelKey(metric) + "|";
              boolean metricCovered = covered(metric.coverage());
              add(values, prefix + "mean", metric.mean(), metricCovered);
              add(values, prefix + "maximum", metric.maximum(), metricCovered);
              add(
                  values,
                  prefix + "populationStandardDeviation",
                  metric.populationStandardDeviation(),
                  metricCovered);
            });
    EnumSet<CycleMetric> identifiedMetricKinds =
        identifiedMetrics.stream()
            .map(MetricFeature::metric)
            .collect(
                java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(CycleMetric.class)));
    EnumSet.complementOf(identifiedMetricKinds)
        .forEach(
            metric -> {
              String prefix = "metric|" + metric.name() + "|UNAVAILABLE_CHANNEL|";
              add(values, prefix + "mean", null, false);
              add(values, prefix + "maximum", null, false);
              add(values, prefix + "populationStandardDeviation", null, false);
            });
    return values;
  }

  private static String channelKey(MetricFeature metric) {
    return String.join(
        "|",
        metric.metric().name(),
        metric.componentId(),
        metric.sourceDataItemId(),
        metric.unit());
  }

  private static boolean covered(FeatureCoverage coverage) {
    return coverage != null
        && coverage.ratio() != null
        && coverage.ratio().compareTo(new BigDecimal("0.800000")) >= 0;
  }

  private static void add(
      Map<String, ScalarFeature> values, String key, BigDecimal value, boolean isEligible) {
    values.put(key, new ScalarFeature(key, value, isEligible));
  }

  private static BigDecimal median(List<BigDecimal> sortedValues) {
    int middle = sortedValues.size() / 2;
    if (sortedValues.size() % 2 == 1) return normalized(sortedValues.get(middle));
    return normalized(
        sortedValues.get(middle - 1).add(sortedValues.get(middle)).divide(BigDecimal.valueOf(2)));
  }

  static BigDecimal normalized(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP);
  }

  public static String lengthPrefixed(Object... fields) {
    StringBuilder material = new StringBuilder();
    for (Object field : fields) {
      if (field == null) {
        material.append("null;");
      } else {
        String value = field.toString();
        material
            .append(value.getBytes(StandardCharsets.UTF_8).length)
            .append(':')
            .append(value)
            .append(';');
      }
    }
    return material.toString();
  }

  private record ScalarFeature(String key, BigDecimal value, boolean isEligible) {}

  private record Sample(String featureSetId, ScalarFeature feature) {}
}
