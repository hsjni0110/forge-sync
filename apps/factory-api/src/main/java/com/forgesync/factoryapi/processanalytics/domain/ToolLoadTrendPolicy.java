package com.forgesync.factoryapi.processanalytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ToolLoadTrendPolicy {
  public static final String POLICY_VERSION = "1.0.0";
  public static final int MINIMUM_RAW_SAMPLES_PER_POINT = 3;
  public static final int MINIMUM_TREND_POINTS = 5;
  public static final int BASELINE_POINT_COUNT = 3;
  public static final BigDecimal MINIMUM_COVERAGE_RATIO = new BigDecimal("0.800000");
  private static final int SCALE = 6;

  public ToolLoadTrendReport project(
      String machineId,
      String replaySessionId,
      long throughReplaySequence,
      String machiningRunProcessingRunId,
      List<ToolLoadTrendReport.MachiningRunInput> runs,
      List<ToolLoadTrendReport.ToolNumberObservation> toolNumbers,
      List<ToolLoadTrendReport.LoadObservation> loads) {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(machiningRunProcessingRunId, "machiningRunProcessingRunId");
    requireOneRealSource(loads);
    List<ToolLoadTrendReport.MachiningRunInput> eligibleRuns =
        runs.stream()
            .filter(run -> "COMPLETED".equals(run.status()))
            .filter(run -> run.programName() != null && run.endedAt() != null)
            .sorted(
                Comparator.comparing(ToolLoadTrendReport.MachiningRunInput::startedAt)
                    .thenComparing(ToolLoadTrendReport.MachiningRunInput::machiningRunId))
            .toList();
    List<ToolLoadTrendReport.ToolNumberObservation> orderedTools =
        toolNumbers.stream().sorted(toolOrder()).toList();
    List<ToolLoadTrendReport.LoadObservation> orderedLoads =
        loads.stream().sorted(loadOrder()).toList();
    Map<CandidateKey, Candidate> candidates = candidates(eligibleRuns, orderedTools, orderedLoads);
    List<ToolLoadTrendReport.Group> groups = groups(candidates);
    ToolLoadTrendReport.LoadObservation source = orderedLoads.stream().findFirst().orElse(null);
    return new ToolLoadTrendReport(
        POLICY_VERSION,
        machineId,
        replaySessionId,
        throughReplaySequence,
        machiningRunProcessingRunId,
        policyDisclosure(),
        provenance(source),
        groups);
  }

  private static void requireOneRealSource(List<ToolLoadTrendReport.LoadObservation> loads) {
    if (loads.stream().anyMatch(load -> !"REAL".equals(load.sourceKind()))) {
      throw new UnsupportedProcessSourceException(
          "Tool load trends require REAL Canonical Observations");
    }
    long sourceCount =
        loads.stream()
            .map(load -> load.provider() + "\u0000" + load.sourceSetId())
            .distinct()
            .count();
    if (sourceCount > 1) {
      throw new UnsupportedProcessSourceException(
          "Tool load trends cannot combine different source identities");
    }
  }

  private static Map<CandidateKey, Candidate> candidates(
      List<ToolLoadTrendReport.MachiningRunInput> runs,
      List<ToolLoadTrendReport.ToolNumberObservation> tools,
      List<ToolLoadTrendReport.LoadObservation> loads) {
    Map<CandidateKey, Candidate> candidates = new LinkedHashMap<>();
    int toolIndex = 0;
    Integer activeTool = null;
    for (ToolLoadTrendReport.LoadObservation load : loads) {
      while (toolIndex < tools.size() && beforeOrAt(tools.get(toolIndex), load)) {
        ToolLoadTrendReport.ToolNumberObservation tool = tools.get(toolIndex++);
        activeTool = tool.available() ? tool.value() : null;
      }
      if (activeTool == null || load.componentId() == null || load.sourceDataItemId() == null) {
        continue;
      }
      ToolLoadTrendReport.MachiningRunInput run = enclosingRun(load.sourceObservedAt(), runs);
      if (run == null) continue;
      CandidateKey key =
          new CandidateKey(
              run,
              activeTool,
              load.componentId(),
              load.sourceDataItemId(),
              load.unit() == null ? "PERCENT" : load.unit());
      candidates.computeIfAbsent(key, ignored -> new Candidate()).observations.add(load);
    }
    return candidates;
  }

  private static boolean beforeOrAt(
      ToolLoadTrendReport.ToolNumberObservation tool, ToolLoadTrendReport.LoadObservation load) {
    int time = tool.sourceObservedAt().compareTo(load.sourceObservedAt());
    return time < 0 || (time == 0 && tool.replaySequence() <= load.replaySequence());
  }

  private static ToolLoadTrendReport.MachiningRunInput enclosingRun(
      Instant observedAt, List<ToolLoadTrendReport.MachiningRunInput> runs) {
    return runs.stream()
        .filter(run -> !observedAt.isBefore(run.startedAt()))
        .filter(run -> !observedAt.isAfter(run.endedAt()))
        .findFirst()
        .orElse(null);
  }

  private static List<ToolLoadTrendReport.Group> groups(Map<CandidateKey, Candidate> candidates) {
    Map<GroupKey, List<PointCalculation>> grouped = new LinkedHashMap<>();
    candidates.forEach(
        (key, candidate) -> {
          GroupKey groupKey =
              new GroupKey(
                  key.run.programName(),
                  key.toolNumber,
                  key.componentId,
                  key.sourceDataItemId,
                  key.unit);
          grouped
              .computeIfAbsent(groupKey, ignored -> new ArrayList<>())
              .add(point(key.run, candidate.observations));
        });
    return grouped.entrySet().stream()
        .map(entry -> group(entry.getKey(), entry.getValue()))
        .sorted(groupOrder())
        .toList();
  }

  private static PointCalculation point(
      ToolLoadTrendReport.MachiningRunInput run,
      List<ToolLoadTrendReport.LoadObservation> observations) {
    List<ToolLoadTrendReport.LoadObservation> available =
        observations.stream()
            .filter(ToolLoadTrendReport.LoadObservation::available)
            .filter(observation -> observation.value() != null)
            .sorted(loadOrder())
            .toList();
    BigDecimal median =
        available.size() < MINIMUM_RAW_SAMPLES_PER_POINT
            ? null
            : median(available.stream().map(ToolLoadTrendReport.LoadObservation::value).toList());
    List<ToolLoadTrendReport.LoadObservation> ordered =
        observations.stream().sorted(loadOrder()).toList();
    return new PointCalculation(
        run,
        available.size(),
        observations.size(),
        median,
        evidence(ordered.getFirst()),
        evidence(ordered.getLast()));
  }

  private static ToolLoadTrendReport.Group group(
      GroupKey key, List<PointCalculation> unsortedPoints) {
    List<PointCalculation> points =
        unsortedPoints.stream()
            .sorted(
                Comparator.comparing((PointCalculation point) -> point.run.startedAt())
                    .thenComparing(point -> point.run.machiningRunId()))
            .toList();
    List<PointCalculation> eligible =
        points.stream().filter(point -> point.median != null).toList();
    BigDecimal coverage = ratio(eligible.size(), points.size());
    BigDecimal baseline =
        eligible.size() < BASELINE_POINT_COUNT
            ? null
            : median(
                eligible.subList(0, BASELINE_POINT_COUNT).stream()
                    .map(point -> point.median)
                    .toList());
    List<String> reasons = new ArrayList<>();
    if (eligible.size() < MINIMUM_TREND_POINTS) reasons.add("FEWER_THAN_5_ELIGIBLE_POINTS");
    if (coverage.compareTo(MINIMUM_COVERAGE_RATIO) < 0) reasons.add("COVERAGE_BELOW_0_8");
    if (baseline != null && baseline.signum() == 0) reasons.add("ZERO_BASELINE");
    String status = status(eligible.size(), coverage, baseline);
    List<BigDecimal> deviations =
        baseline == null || baseline.signum() == 0
            ? List.of()
            : eligible.stream().map(point -> deviation(point.median, baseline)).toList();
    BigDecimal latest = "AVAILABLE".equals(status) ? deviations.getLast() : null;
    BigDecimal slope = "AVAILABLE".equals(status) ? slope(deviations) : null;
    Map<String, BigDecimal> deviationByRun = new LinkedHashMap<>();
    for (int index = 0; index < eligible.size() && index < deviations.size(); index++) {
      deviationByRun.put(eligible.get(index).run.machiningRunId(), deviations.get(index));
    }
    return new ToolLoadTrendReport.Group(
        key.programName,
        key.toolNumber,
        key.componentId,
        key.sourceDataItemId,
        key.unit,
        status,
        List.copyOf(reasons),
        points.size(),
        eligible.size(),
        coverage,
        baseline,
        latest,
        slope,
        points.stream()
            .map(point -> point.toReport(deviationByRun.get(point.run.machiningRunId())))
            .toList());
  }

  private static String status(int eligibleCount, BigDecimal coverage, BigDecimal baseline) {
    if (eligibleCount < MINIMUM_TREND_POINTS) return "INSUFFICIENT_SAMPLES";
    if (coverage.compareTo(MINIMUM_COVERAGE_RATIO) < 0) return "INSUFFICIENT_COVERAGE";
    if (baseline == null || baseline.signum() == 0) return "BASELINE_UNAVAILABLE";
    return "AVAILABLE";
  }

  private static BigDecimal ratio(int numerator, int denominator) {
    if (denominator == 0) return BigDecimal.ZERO.setScale(SCALE);
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal deviation(BigDecimal value, BigDecimal baseline) {
    return value
        .subtract(baseline)
        .multiply(BigDecimal.valueOf(100))
        .divide(baseline, SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal slope(List<BigDecimal> values) {
    BigDecimal count = BigDecimal.valueOf(values.size());
    BigDecimal sumX = BigDecimal.ZERO;
    BigDecimal sumY = BigDecimal.ZERO;
    BigDecimal sumXY = BigDecimal.ZERO;
    BigDecimal sumXX = BigDecimal.ZERO;
    for (int index = 0; index < values.size(); index++) {
      BigDecimal x = BigDecimal.valueOf(index);
      BigDecimal y = values.get(index);
      sumX = sumX.add(x);
      sumY = sumY.add(y);
      sumXY = sumXY.add(x.multiply(y));
      sumXX = sumXX.add(x.multiply(x));
    }
    BigDecimal denominator = count.multiply(sumXX).subtract(sumX.multiply(sumX));
    if (denominator.signum() == 0) return BigDecimal.ZERO.setScale(SCALE);
    return count
        .multiply(sumXY)
        .subtract(sumX.multiply(sumY))
        .divide(denominator, SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal median(List<BigDecimal> values) {
    List<BigDecimal> sorted = values.stream().sorted().toList();
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) return sorted.get(middle).setScale(SCALE, RoundingMode.HALF_UP);
    return sorted
        .get(middle - 1)
        .add(sorted.get(middle))
        .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
  }

  private static ToolLoadTrendReport.Evidence evidence(
      ToolLoadTrendReport.LoadObservation observation) {
    return new ToolLoadTrendReport.Evidence(
        observation.replaySequence(),
        observation.sourceObservedAt(),
        observation.sourceEventKey(),
        observation.rawRecordId(),
        observation.sourceDataItemId(),
        observation.mappingVersion());
  }

  private static ToolLoadTrendReport.Policy policyDisclosure() {
    return new ToolLoadTrendReport.Policy(
        MINIMUM_RAW_SAMPLES_PER_POINT,
        MINIMUM_TREND_POINTS,
        BASELINE_POINT_COUNT,
        MINIMUM_COVERAGE_RATIO,
        "median(available PERCENT load samples in one completed run and active tool)",
        "eligible observed run-tool-channel points / observed run-tool-channel candidate points",
        "((pointMedian - baselineMedian) / baselineMedian) * 100",
        "ordinary least squares slope of deviationPercent by eligible point ordinal");
  }

  private static ToolLoadTrendReport.Provenance provenance(
      ToolLoadTrendReport.LoadObservation source) {
    return source == null
        ? new ToolLoadTrendReport.Provenance("DERIVED", "UNKNOWN", "UNKNOWN", "UNKNOWN")
        : new ToolLoadTrendReport.Provenance(
            "DERIVED", source.sourceKind(), source.provider(), source.sourceSetId());
  }

  private static Comparator<ToolLoadTrendReport.ToolNumberObservation> toolOrder() {
    return Comparator.comparing(ToolLoadTrendReport.ToolNumberObservation::sourceObservedAt)
        .thenComparingLong(ToolLoadTrendReport.ToolNumberObservation::replaySequence);
  }

  private static Comparator<ToolLoadTrendReport.LoadObservation> loadOrder() {
    return Comparator.comparing(ToolLoadTrendReport.LoadObservation::sourceObservedAt)
        .thenComparingLong(ToolLoadTrendReport.LoadObservation::replaySequence)
        .thenComparing(ToolLoadTrendReport.LoadObservation::sourceEventKey);
  }

  private static Comparator<ToolLoadTrendReport.Group> groupOrder() {
    return Comparator.comparing(ToolLoadTrendReport.Group::programName)
        .thenComparingInt(ToolLoadTrendReport.Group::toolNumber)
        .thenComparing(ToolLoadTrendReport.Group::componentId)
        .thenComparing(ToolLoadTrendReport.Group::sourceDataItemId);
  }

  private record CandidateKey(
      ToolLoadTrendReport.MachiningRunInput run,
      int toolNumber,
      String componentId,
      String sourceDataItemId,
      String unit) {}

  private record GroupKey(
      String programName,
      int toolNumber,
      String componentId,
      String sourceDataItemId,
      String unit) {}

  private static final class Candidate {
    private final List<ToolLoadTrendReport.LoadObservation> observations = new ArrayList<>();
  }

  private record PointCalculation(
      ToolLoadTrendReport.MachiningRunInput run,
      int availableSampleCount,
      int totalObservationCount,
      BigDecimal median,
      ToolLoadTrendReport.Evidence firstEvidence,
      ToolLoadTrendReport.Evidence lastEvidence) {
    private ToolLoadTrendReport.Point toReport(BigDecimal deviation) {
      return new ToolLoadTrendReport.Point(
          run.machiningRunId(),
          median == null ? "INSUFFICIENT_SAMPLES" : "AVAILABLE",
          availableSampleCount,
          totalObservationCount,
          median,
          deviation,
          firstEvidence,
          lastEvidence);
    }
  }
}
