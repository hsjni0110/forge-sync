package com.forgesync.factoryapi.production.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ObservedProductionContextPolicy {
  public static final String RULE_VERSION = "1.0.0";

  public ObservedProductionContext project(
      String machineId,
      String replaySessionId,
      long throughReplaySequence,
      String machiningRunProcessingRunId,
      List<ObservedProductionContext.ProgramObservation> programs,
      List<ObservedProductionContext.ObservedMachiningRun> runs,
      List<ObservedProductionContext.PartCountObservation> partCounts) {
    Objects.requireNonNull(machineId);
    Objects.requireNonNull(replaySessionId);
    Objects.requireNonNull(machiningRunProcessingRunId);
    List<ObservedProductionContext.ObservedMachiningRun> orderedRuns =
        runs.stream()
            .sorted(
                Comparator.comparing(ObservedProductionContext.ObservedMachiningRun::startedAt)
                    .thenComparing(ObservedProductionContext.ObservedMachiningRun::machiningRunId))
            .toList();
    return new ObservedProductionContext(
        RULE_VERSION,
        machineId,
        replaySessionId,
        throughReplaySequence,
        machiningRunProcessingRunId,
        intervals(programs),
        summaries(orderedRuns),
        (int) orderedRuns.stream().filter(run -> run.programName() == null).count(),
        partCount(partCounts, orderedRuns));
  }

  private static List<ObservedProductionContext.ProgramInterval> intervals(
      List<ObservedProductionContext.ProgramObservation> observations) {
    List<ObservedProductionContext.ProgramInterval> result = new ArrayList<>();
    for (String kind : List.of("MAIN", "SUBPROGRAM")) {
      List<ObservedProductionContext.ProgramObservation> ordered =
          observations.stream()
              .filter(item -> kind.equals(item.kind()))
              .sorted(observationOrder())
              .toList();
      ObservedProductionContext.ProgramObservation start = null;
      for (var current : ordered) {
        if (start != null && sameProgramState(start, current)) continue;
        if (start != null) result.add(interval(start, current));
        start = current;
      }
      if (start != null) result.add(interval(start, null));
    }
    return List.copyOf(result);
  }

  private static Comparator<ObservedProductionContext.ProgramObservation> observationOrder() {
    return Comparator.comparingLong(ObservedProductionContext.ProgramObservation::replaySequence)
        .thenComparing(ObservedProductionContext.ProgramObservation::sourceObservedAt)
        .thenComparing(ObservedProductionContext.ProgramObservation::sourceEventKey);
  }

  private static boolean sameProgramState(
      ObservedProductionContext.ProgramObservation left,
      ObservedProductionContext.ProgramObservation right) {
    return left.isAvailable() == right.isAvailable() && Objects.equals(left.value(), right.value());
  }

  private static ObservedProductionContext.ProgramInterval interval(
      ObservedProductionContext.ProgramObservation start,
      ObservedProductionContext.ProgramObservation end) {
    return new ObservedProductionContext.ProgramInterval(
        start.kind(),
        start.isAvailable() ? "AVAILABLE" : "UNAVAILABLE",
        start.value(),
        start.sourceObservedAt(),
        end == null ? null : end.sourceObservedAt(),
        evidence(start),
        end == null ? null : evidence(end));
  }

  private static List<ObservedProductionContext.ProgramSummary> summaries(
      List<ObservedProductionContext.ObservedMachiningRun> runs) {
    Map<String, List<ObservedProductionContext.ObservedMachiningRun>> grouped =
        new LinkedHashMap<>();
    runs.stream()
        .filter(run -> run.programName() != null)
        .forEach(
            run ->
                grouped.computeIfAbsent(run.programName(), ignored -> new ArrayList<>()).add(run));
    return grouped.entrySet().stream()
        .map(entry -> summary(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(ObservedProductionContext.ProgramSummary::programName))
        .toList();
  }

  private static ObservedProductionContext.ProgramSummary summary(
      String programName, List<ObservedProductionContext.ObservedMachiningRun> runs) {
    List<BigDecimal> completedDurations =
        runs.stream()
            .filter(run -> "COMPLETED".equals(run.status()) && run.endedAt() != null)
            .map(
                run ->
                    BigDecimal.valueOf(
                        Duration.between(run.startedAt(), run.endedAt()).toMillis(), 3))
            .sorted()
            .toList();
    BigDecimal total = completedDurations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal mean =
        completedDurations.isEmpty()
            ? null
            : total.divide(BigDecimal.valueOf(completedDurations.size()), 6, RoundingMode.HALF_UP);
    BigDecimal median = median(completedDurations);
    return new ObservedProductionContext.ProgramSummary(
        programName,
        runs.size(),
        completedDurations.size(),
        total,
        mean,
        median,
        runs.stream().map(ObservedProductionContext.ObservedMachiningRun::machiningRunId).toList());
  }

  private static BigDecimal median(List<BigDecimal> sorted) {
    if (sorted.isEmpty()) return null;
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) return sorted.get(middle).setScale(6, RoundingMode.HALF_UP);
    return sorted
        .get(middle - 1)
        .add(sorted.get(middle))
        .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
  }

  private static ObservedProductionContext.PartCountSummary partCount(
      List<ObservedProductionContext.PartCountObservation> observations,
      List<ObservedProductionContext.ObservedMachiningRun> runs) {
    List<ObservedProductionContext.PartCountObservation> ordered =
        observations.stream()
            .sorted(
                Comparator.comparingLong(
                        ObservedProductionContext.PartCountObservation::replaySequence)
                    .thenComparing(ObservedProductionContext.PartCountObservation::sourceObservedAt)
                    .thenComparing(ObservedProductionContext.PartCountObservation::sourceEventKey))
            .toList();
    BigDecimal net = BigDecimal.ZERO;
    int resets = 0;
    int unavailable = 0;
    ObservedProductionContext.PartCountObservation previous = null;
    List<ObservedProductionContext.PartCountAssociation> associations = new ArrayList<>();
    for (var current : ordered) {
      if (!current.isAvailable()) {
        unavailable++;
        previous = null;
        continue;
      }
      if (previous != null) {
        BigDecimal delta = current.value().subtract(previous.value());
        if (delta.signum() < 0) resets++;
        else {
          net = net.add(delta);
          associations.add(
              new ObservedProductionContext.PartCountAssociation(
                  delta,
                  evidence(previous),
                  evidence(current),
                  "TEMPORAL_OVERLAP_ONLY",
                  overlappingRuns(previous.sourceObservedAt(), current.sourceObservedAt(), runs)));
        }
      }
      previous = current;
    }
    if (associations.isEmpty()) {
      return new ObservedProductionContext.PartCountSummary(
          "UNAVAILABLE", null, 0, resets, unavailable, "NO_USABLE_TRANSITIONS", List.of());
    }
    return new ObservedProductionContext.PartCountSummary(
        "AVAILABLE",
        net.stripTrailingZeros(),
        associations.size(),
        resets,
        unavailable,
        null,
        List.copyOf(associations));
  }

  private static List<String> overlappingRuns(
      java.time.Instant from,
      java.time.Instant to,
      List<ObservedProductionContext.ObservedMachiningRun> runs) {
    return runs.stream()
        .filter(run -> run.endedAt() == null || !run.endedAt().isBefore(from))
        .filter(run -> !run.startedAt().isAfter(to))
        .map(ObservedProductionContext.ObservedMachiningRun::machiningRunId)
        .toList();
  }

  private static ObservedProductionContext.Evidence evidence(
      ObservedProductionContext.ProgramObservation observation) {
    return new ObservedProductionContext.Evidence(
        observation.replaySequence(),
        observation.sourceObservedAt(),
        observation.sourceEventKey(),
        observation.rawRecordId(),
        observation.sourceDataItemId());
  }

  private static ObservedProductionContext.Evidence evidence(
      ObservedProductionContext.PartCountObservation observation) {
    return new ObservedProductionContext.Evidence(
        observation.replaySequence(),
        observation.sourceObservedAt(),
        observation.sourceEventKey(),
        observation.rawRecordId(),
        observation.sourceDataItemId());
  }
}
