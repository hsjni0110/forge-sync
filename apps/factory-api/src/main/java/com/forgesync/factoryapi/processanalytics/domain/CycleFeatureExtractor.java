package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CycleFeatureExtractor {
  public static final String FEATURE_VERSION = "1.0.0";
  private static final int TIME_SCALE = 9;
  private static final int STATISTIC_SCALE = 6;

  public CycleFeature extract(MachiningRun run, List<CycleObservation> observations) {
    Objects.requireNonNull(run);
    if (run.status() != MachiningRunStatus.COMPLETED || run.endedAt() == null) {
      throw new IllegalArgumentException("Cycle features require a COMPLETED MachiningRun");
    }
    List<CycleObservation> ordered = windowInputs(run, observations);
    BigDecimal window = seconds(run.startedAt(), run.endedAt());
    if (window.signum() == 0) {
      return emptyFeature(run, window);
    }

    validateUnits(ordered);
    StateTotals state = stateTotals(run.startedAt(), run.endedAt(), ordered);
    List<MetricFeature> metrics = metricFeatures(run.startedAt(), run.endedAt(), ordered, window);
    List<ObservationProvenance> provenance =
        ordered.stream()
            .filter(
                observation ->
                    contributesToWindow(observation, run.startedAt(), run.endedAt(), ordered))
            .map(CycleObservation::provenance)
            .distinct()
            .toList();
    FeatureAvailability status =
        state.covered.signum() == 0
                && metrics.stream().allMatch(item -> item.status() == FeatureAvailability.MISSING)
            ? FeatureAvailability.MISSING
            : state.covered.compareTo(window) == 0
                    && metrics.stream()
                        .allMatch(item -> item.status() == FeatureAvailability.AVAILABLE)
                ? FeatureAvailability.AVAILABLE
                : FeatureAvailability.PARTIAL;
    FeatureCoverage stateCoverage = coverage(state.covered, window);
    List<ObservationProvenance> stateProvenance =
        ordered.stream()
            .filter(observation -> observation.signal() == CycleSignal.EXECUTION)
            .filter(
                observation ->
                    contributesToWindow(observation, run.startedAt(), run.endedAt(), ordered))
            .map(CycleObservation::provenance)
            .distinct()
            .toList();
    String resultHash =
        DeterministicHash.sha256(
            hashMaterial(run, window, state.cutting, state.idle, stateCoverage, metrics));
    return new CycleFeature(
        FEATURE_VERSION,
        run.machiningRunId(),
        run.startedAt(),
        run.endedAt(),
        status,
        window,
        state.cutting,
        state.idle,
        stateCoverage,
        stateProvenance,
        metrics,
        observationRange(run, ordered),
        provenance,
        resultHash);
  }

  public List<CycleObservation> relevantObservations(
      MachiningRun run, List<CycleObservation> observations) {
    Objects.requireNonNull(run);
    return windowInputs(run, observations);
  }

  private static CycleFeature emptyFeature(MachiningRun run, BigDecimal window) {
    FeatureCoverage coverage = new FeatureCoverage(window, window, null);
    List<MetricFeature> metrics =
        EnumSet.allOf(CycleMetric.class).stream()
            .map(
                metric ->
                    new MetricFeature(
                        metric,
                        null,
                        null,
                        null,
                        FeatureAvailability.EMPTY_WINDOW,
                        "TIME_WEIGHTED_LAST_OBSERVATION_CARRIED_FORWARD",
                        null,
                        null,
                        null,
                        coverage,
                        List.of(),
                        List.of()))
            .toList();
    return new CycleFeature(
        FEATURE_VERSION,
        run.machiningRunId(),
        run.startedAt(),
        run.endedAt(),
        FeatureAvailability.EMPTY_WINDOW,
        window,
        null,
        null,
        coverage,
        List.of(),
        metrics,
        run.observationRange(),
        List.of(),
        DeterministicHash.sha256(hashMaterial(run, window, null, null, coverage, metrics)));
  }

  private static StateTotals stateTotals(
      Instant startedAt, Instant endedAt, List<CycleObservation> observations) {
    List<CycleObservation> states =
        observations.stream().filter(item -> item.signal() == CycleSignal.EXECUTION).toList();
    BigDecimal cutting = zeroTime();
    BigDecimal idle = zeroTime();
    for (int index = 0; index < states.size(); index++) {
      CycleObservation observation = states.get(index);
      Instant from = later(observation.sourceObservedAt(), startedAt);
      Instant until =
          index + 1 < states.size()
              ? earlier(states.get(index + 1).sourceObservedAt(), endedAt)
              : endedAt;
      if (!from.isBefore(until) || !observation.isAvailable()) continue;
      BigDecimal interval = seconds(from, until);
      if ("ACTIVE".equals(observation.textValue())) cutting = cutting.add(interval);
      else idle = idle.add(interval);
    }
    return new StateTotals(cutting, idle, cutting.add(idle));
  }

  private static List<MetricFeature> metricFeatures(
      Instant startedAt, Instant endedAt, List<CycleObservation> observations, BigDecimal window) {
    Map<ChannelCore, List<CycleObservation>> channels =
        observations.stream()
            .filter(observation -> observation.signal().isMetric())
            .collect(
                Collectors.groupingBy(
                    observation ->
                        new ChannelCore(
                            observation.signal().metric(),
                            observation.componentId(),
                            observation.sourceDataItemId()),
                    LinkedHashMap::new,
                    Collectors.toList()));
    List<MetricFeature> result = new ArrayList<>();
    for (Map.Entry<ChannelCore, List<CycleObservation>> entry : channels.entrySet()) {
      String unit =
          entry.getValue().stream()
              .map(CycleObservation::unit)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      ChannelCore core = entry.getKey();
      result.add(
          metricFeature(
              new ChannelKey(core.metric, core.componentId, core.sourceDataItemId, unit),
              entry.getValue(),
              startedAt,
              endedAt,
              window));
    }
    for (CycleMetric metric : CycleMetric.values()) {
      if (result.stream().noneMatch(feature -> feature.metric() == metric)) {
        result.add(missingMetric(metric, window));
      }
    }
    return result.stream()
        .sorted(
            Comparator.comparing(MetricFeature::metric)
                .thenComparing(MetricFeature::componentId, Comparator.nullsLast(String::compareTo))
                .thenComparing(
                    MetricFeature::sourceDataItemId, Comparator.nullsLast(String::compareTo))
                .thenComparing(MetricFeature::unit, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private static MetricFeature metricFeature(
      ChannelKey channel,
      List<CycleObservation> observations,
      Instant startedAt,
      Instant endedAt,
      BigDecimal window) {
    BigDecimal covered = zeroTime();
    BigDecimal weightedSum = BigDecimal.ZERO;
    BigDecimal weightedSquareSum = BigDecimal.ZERO;
    BigDecimal maximum = null;
    List<String> sourceEventKeys = new ArrayList<>();
    List<ObservationProvenance> provenance = new ArrayList<>();
    for (int index = 0; index < observations.size(); index++) {
      CycleObservation observation = observations.get(index);
      Instant from = later(observation.sourceObservedAt(), startedAt);
      Instant until =
          index + 1 < observations.size()
              ? earlier(observations.get(index + 1).sourceObservedAt(), endedAt)
              : endedAt;
      if (!from.isBefore(until) || !observation.isAvailable()) continue;
      BigDecimal interval = seconds(from, until);
      BigDecimal value = observation.numericValue();
      covered = covered.add(interval);
      weightedSum = weightedSum.add(value.multiply(interval));
      weightedSquareSum = weightedSquareSum.add(value.multiply(value).multiply(interval));
      maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
      sourceEventKeys.add(observation.sourceEventKey());
      provenance.add(observation.provenance());
    }
    if (covered.signum() == 0) return missingMetric(channel, window);
    BigDecimal mean = weightedSum.divide(covered, STATISTIC_SCALE, RoundingMode.HALF_UP);
    BigDecimal meanForVariance = weightedSum.divide(covered, 18, RoundingMode.HALF_UP);
    BigDecimal variance =
        weightedSquareSum
            .divide(covered, 18, RoundingMode.HALF_UP)
            .subtract(meanForVariance.multiply(meanForVariance))
            .max(BigDecimal.ZERO);
    BigDecimal standardDeviation =
        variance
            .sqrt(new MathContext(30, RoundingMode.HALF_UP))
            .setScale(STATISTIC_SCALE, RoundingMode.HALF_UP);
    FeatureCoverage coverage = coverage(covered, window);
    return new MetricFeature(
        channel.metric,
        channel.componentId,
        channel.sourceDataItemId,
        channel.unit,
        covered.compareTo(window) == 0
            ? FeatureAvailability.AVAILABLE
            : FeatureAvailability.PARTIAL,
        "TIME_WEIGHTED_LAST_OBSERVATION_CARRIED_FORWARD",
        mean,
        maximum.stripTrailingZeros(),
        standardDeviation,
        coverage,
        sourceEventKeys,
        provenance.stream().distinct().toList());
  }

  private static MetricFeature missingMetric(CycleMetric metric, BigDecimal window) {
    return missingMetric(new ChannelKey(metric, null, null, null), window);
  }

  private static MetricFeature missingMetric(ChannelKey channel, BigDecimal window) {
    return new MetricFeature(
        channel.metric,
        channel.componentId,
        channel.sourceDataItemId,
        channel.unit,
        FeatureAvailability.MISSING,
        "TIME_WEIGHTED_LAST_OBSERVATION_CARRIED_FORWARD",
        null,
        null,
        null,
        coverage(zeroTime(), window),
        List.of(),
        List.of());
  }

  private static ObservationRange observationRange(
      MachiningRun run, List<CycleObservation> observations) {
    List<CycleObservation> contributing =
        observations.stream()
            .filter(
                observation ->
                    contributesToWindow(observation, run.startedAt(), run.endedAt(), observations))
            .sorted(
                Comparator.comparingLong(CycleObservation::replaySequence)
                    .thenComparing(CycleObservation::sourceObservedAt)
                    .thenComparing(CycleObservation::sourceEventKey))
            .toList();
    if (contributing.isEmpty()) return run.observationRange();
    CycleObservation first = contributing.getFirst();
    CycleObservation last = contributing.getLast();
    return new ObservationRange(
        first.replaySessionId(),
        first.replaySequence(),
        last.replaySequence(),
        first.sourceObservedAt(),
        last.sourceObservedAt(),
        first.sourceEventKey(),
        last.sourceEventKey());
  }

  private static void validateUnits(List<CycleObservation> observations) {
    Map<String, Set<String>> units =
        observations.stream()
            .filter(observation -> observation.signal().isMetric() && observation.unit() != null)
            .collect(
                Collectors.groupingBy(
                    observation ->
                        observation.signal()
                            + "|"
                            + observation.componentId()
                            + "|"
                            + observation.sourceDataItemId(),
                    Collectors.mapping(CycleObservation::unit, Collectors.toSet())));
    units.forEach(
        (channel, channelUnits) -> {
          if (channelUnits.size() > 1) throw new CycleFeatureUnitMismatchException(channel);
        });
  }

  private static FeatureCoverage coverage(BigDecimal covered, BigDecimal window) {
    BigDecimal ratio =
        window.signum() == 0 ? null : covered.divide(window, STATISTIC_SCALE, RoundingMode.HALF_UP);
    return new FeatureCoverage(covered, window, ratio);
  }

  private static boolean contributesToWindow(
      CycleObservation candidate,
      Instant startedAt,
      Instant endedAt,
      List<CycleObservation> observations) {
    if (!candidate.sourceObservedAt().isBefore(endedAt)) return false;
    if (!candidate.sourceObservedAt().isBefore(startedAt)) return true;
    return observations.stream()
        .filter(other -> sameChannel(candidate, other))
        .filter(other -> !other.sourceObservedAt().isAfter(startedAt))
        .max(comparatorForLatest())
        .filter(candidate::equals)
        .isPresent();
  }

  private static boolean sameChannel(CycleObservation left, CycleObservation right) {
    return left.signal() == right.signal()
        && left.componentId().equals(right.componentId())
        && left.sourceDataItemId().equals(right.sourceDataItemId());
  }

  private static List<CycleObservation> windowInputs(
      MachiningRun run, List<CycleObservation> observations) {
    List<CycleObservation> sessionInputs =
        observations.stream()
            .filter(
                observation ->
                    observation.replaySessionId().equals(run.observationRange().replaySessionId()))
            .filter(observation -> observation.sourceObservedAt().isBefore(run.endedAt()))
            .toList();
    Map<String, CycleObservation> carryIn = new LinkedHashMap<>();
    sessionInputs.stream()
        .filter(observation -> observation.sourceObservedAt().isBefore(run.startedAt()))
        .forEach(
            observation ->
                carryIn.merge(
                    channelIdentity(observation),
                    observation,
                    (left, right) ->
                        comparatorForLatest().compare(left, right) < 0 ? right : left));
    return sessionInputs.stream()
        .filter(
            observation ->
                !observation.sourceObservedAt().isBefore(run.startedAt())
                    || carryIn.get(channelIdentity(observation)).equals(observation))
        .sorted(observationOrder())
        .toList();
  }

  private static String channelIdentity(CycleObservation observation) {
    return observation.signal()
        + "\n"
        + observation.componentId()
        + "\n"
        + observation.sourceDataItemId();
  }

  private static Comparator<CycleObservation> observationOrder() {
    return Comparator.comparing(CycleObservation::sourceObservedAt)
        .thenComparingLong(CycleObservation::replaySequence)
        .thenComparing(CycleObservation::sourceEventKey);
  }

  private static Comparator<CycleObservation> comparatorForLatest() {
    return Comparator.comparing(CycleObservation::sourceObservedAt)
        .thenComparingLong(CycleObservation::replaySequence)
        .thenComparing(CycleObservation::sourceEventKey);
  }

  private static BigDecimal seconds(Instant from, Instant until) {
    Duration duration = Duration.between(from, until);
    return BigDecimal.valueOf(duration.getSeconds())
        .add(BigDecimal.valueOf(duration.getNano(), TIME_SCALE))
        .setScale(TIME_SCALE, RoundingMode.UNNECESSARY);
  }

  private static BigDecimal zeroTime() {
    return BigDecimal.ZERO.setScale(TIME_SCALE);
  }

  private static Instant later(Instant left, Instant right) {
    return left.isAfter(right) ? left : right;
  }

  private static Instant earlier(Instant left, Instant right) {
    return left.isBefore(right) ? left : right;
  }

  private static String hashMaterial(
      MachiningRun run,
      BigDecimal duration,
      BigDecimal cutting,
      BigDecimal idle,
      FeatureCoverage coverage,
      List<MetricFeature> metrics) {
    StringBuilder material = new StringBuilder("cycle-feature:1;");
    append(material, FEATURE_VERSION);
    append(material, run.machiningRunId());
    append(material, run.startedAt());
    append(material, run.endedAt());
    append(material, duration);
    append(material, cutting);
    append(material, idle);
    append(material, coverage);
    metrics.forEach(metric -> append(material, metric));
    return material.toString();
  }

  private static void append(StringBuilder material, Object field) {
    if (field == null) {
      material.append("null;");
      return;
    }
    String value = field.toString();
    material
        .append(value.getBytes(StandardCharsets.UTF_8).length)
        .append(':')
        .append(value)
        .append(';');
  }

  private record StateTotals(BigDecimal cutting, BigDecimal idle, BigDecimal covered) {}

  private record ChannelKey(
      CycleMetric metric, String componentId, String sourceDataItemId, String unit) {}

  private record ChannelCore(CycleMetric metric, String componentId, String sourceDataItemId) {}
}
