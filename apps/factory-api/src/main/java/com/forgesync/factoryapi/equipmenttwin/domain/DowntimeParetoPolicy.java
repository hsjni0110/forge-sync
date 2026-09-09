package com.forgesync.factoryapi.equipmenttwin.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DowntimeParetoPolicy {

  public static final String RULE_VERSION = "1.0.0";

  public DowntimeParetoReport rank(
      String utilizationProcessingRunId,
      String intervalProcessingRunId,
      EquipmentStateIntervalReport intervals,
      List<ConditionEvidenceObservation> conditions) {
    Objects.requireNonNull(utilizationProcessingRunId);
    Objects.requireNonNull(intervalProcessingRunId);
    Objects.requireNonNull(intervals);
    Objects.requireNonNull(conditions);
    requireSameSource(intervals, conditions);

    List<EquipmentStateInterval> candidates =
        intervals.intervals().stream()
            .filter(interval -> interval.signal() == StateSignal.EXECUTION)
            .filter(interval -> !interval.isOpen())
            .filter(interval -> isDowntime(UtilizationKpiPolicy.utilizationStateOf(interval)))
            .sorted(
                Comparator.comparing(
                        (EquipmentStateInterval interval) -> interval.duration().orElseThrow())
                    .reversed()
                    .thenComparing(EquipmentStateInterval::startedAt)
                    .thenComparingLong(interval -> interval.startEvidence().replaySequence())
                    .thenComparing(interval -> interval.startEvidence().sourceEventKey()))
            .toList();
    BigDecimal total =
        candidates.stream()
            .map(interval -> seconds(interval.duration().orElseThrow()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(6, RoundingMode.HALF_UP);
    List<EquipmentStateInterval> modes =
        intervals.intervals().stream()
            .filter(interval -> interval.signal() == StateSignal.CONTROLLER_MODE)
            .sorted(Comparator.comparing(EquipmentStateInterval::startedAt))
            .toList();

    List<DowntimeParetoEntry> entries = new ArrayList<>();
    BigDecimal cumulative = BigDecimal.ZERO;
    for (int index = 0; index < candidates.size(); index++) {
      EquipmentStateInterval candidate = candidates.get(index);
      BigDecimal duration = seconds(candidate.duration().orElseThrow());
      cumulative = cumulative.add(duration);
      List<DowntimeEvidence> evidence =
          evidenceFor(candidate, intervals.intervals(), modes, conditions);
      entries.add(
          new DowntimeParetoEntry(
              index + 1,
              UtilizationKpiPolicy.utilizationStateOf(candidate),
              candidate.startedAt(),
              candidate.endedAt(),
              candidate.duration().orElseThrow(),
              percent(duration, total),
              percent(cumulative, total),
              candidate.startEvidence(),
              candidate.endEvidence(),
              evidence.isEmpty()
                  ? DowntimeReasonClassification.UNCONFIRMED_REASON
                  : DowntimeReasonClassification.CONCURRENT_EVIDENCE,
              evidence));
    }
    String inputHash =
        inputHash(utilizationProcessingRunId, intervalProcessingRunId, intervals, conditions);
    String resultHash = resultHash(entries);
    return new DowntimeParetoReport(
        RULE_VERSION,
        utilizationProcessingRunId,
        intervalProcessingRunId,
        intervals.machineId(),
        intervals.replaySessionId(),
        intervals.throughReplaySequence(),
        intervals.observedFrom(),
        intervals.observedTo(),
        total,
        inputHash,
        resultHash,
        entries);
  }

  private static List<DowntimeEvidence> evidenceFor(
      EquipmentStateInterval downtime,
      List<EquipmentStateInterval> allIntervals,
      List<EquipmentStateInterval> modes,
      List<ConditionEvidenceObservation> conditions) {
    List<DowntimeEvidence> evidence = new ArrayList<>();
    allIntervals.stream()
        .filter(interval -> interval.signal() == StateSignal.EMERGENCY_STOP)
        .filter(interval -> "TRIGGERED".equals(interval.value()))
        .filter(interval -> overlaps(interval, downtime))
        .map(DowntimeParetoPolicy::estopEvidence)
        .forEach(evidence::add);
    modes.stream()
        .skip(1)
        .filter(interval -> contains(downtime, interval.startedAt()))
        .map(DowntimeParetoPolicy::modeEvidence)
        .forEach(evidence::add);
    conditions.stream()
        .filter(observation -> isAttentionLevel(observation.level()))
        .filter(observation -> contains(downtime, observation.sourceObservedAt()))
        .map(DowntimeParetoPolicy::conditionEvidence)
        .forEach(evidence::add);
    evidence.sort(
        Comparator.comparing(DowntimeEvidence::kind)
            .thenComparing(DowntimeEvidence::sourceObservedAt)
            .thenComparingLong(DowntimeEvidence::replaySequence)
            .thenComparing(DowntimeEvidence::sourceEventKey));
    return List.copyOf(evidence);
  }

  private static DowntimeEvidence estopEvidence(EquipmentStateInterval interval) {
    return new DowntimeEvidence(
        DowntimeEvidenceKind.ESTOP_OVERLAP,
        interval.signal().name(),
        interval.value(),
        interval.startedAt(),
        interval.startEvidence().replaySequence(),
        interval.startEvidence().sourceEventKey(),
        null,
        null,
        null,
        null,
        null);
  }

  private static DowntimeEvidence modeEvidence(EquipmentStateInterval interval) {
    return new DowntimeEvidence(
        DowntimeEvidenceKind.MODE_CHANGE,
        interval.signal().name(),
        interval.value(),
        interval.startedAt(),
        interval.startEvidence().replaySequence(),
        interval.startEvidence().sourceEventKey(),
        null,
        null,
        null,
        null,
        null);
  }

  private static DowntimeEvidence conditionEvidence(ConditionEvidenceObservation observation) {
    return new DowntimeEvidence(
        DowntimeEvidenceKind.CONDITION_OBSERVATION,
        "CONDITION",
        observation.level(),
        observation.sourceObservedAt(),
        observation.replaySequence(),
        observation.sourceEventKey(),
        observation.componentId(),
        observation.conditionType(),
        observation.level(),
        observation.nativeCode(),
        observation.message());
  }

  private static boolean overlaps(
      EquipmentStateInterval evidence, EquipmentStateInterval downtime) {
    Instant evidenceEnd = evidence.endedAt();
    return evidence.startedAt().isBefore(downtime.endedAt())
        && (evidenceEnd == null || evidenceEnd.isAfter(downtime.startedAt()));
  }

  private static boolean contains(EquipmentStateInterval interval, Instant observedAt) {
    return !observedAt.isBefore(interval.startedAt()) && observedAt.isBefore(interval.endedAt());
  }

  private static boolean isAttentionLevel(String level) {
    return level.equals("WARNING") || level.equals("FAULT");
  }

  private static boolean isDowntime(UtilizationState state) {
    return state == UtilizationState.STOPPED
        || state == UtilizationState.INTERRUPTED
        || state == UtilizationState.UNKNOWN;
  }

  private static BigDecimal seconds(Duration duration) {
    return BigDecimal.valueOf(duration.getSeconds())
        .add(BigDecimal.valueOf(duration.getNano(), 9))
        .setScale(6, RoundingMode.HALF_UP);
  }

  private static BigDecimal percent(BigDecimal value, BigDecimal total) {
    if (total.signum() == 0) {
      return BigDecimal.ZERO.setScale(6, RoundingMode.UNNECESSARY);
    }
    return value.multiply(BigDecimal.valueOf(100)).divide(total, 6, RoundingMode.HALF_UP);
  }

  private static void requireSameSource(
      EquipmentStateIntervalReport intervals, List<ConditionEvidenceObservation> conditions) {
    for (ConditionEvidenceObservation condition : conditions) {
      if (!condition.machineId().equals(intervals.machineId())
          || !condition.replaySessionId().equals(intervals.replaySessionId())) {
        throw new IllegalArgumentException("Evidence cannot span machine or Replay Session");
      }
    }
  }

  private static String inputHash(
      String utilizationProcessingRunId,
      String intervalProcessingRunId,
      EquipmentStateIntervalReport intervals,
      List<ConditionEvidenceObservation> conditions) {
    StringBuilder value =
        new StringBuilder(RULE_VERSION)
            .append('\n')
            .append(utilizationProcessingRunId)
            .append('\n')
            .append(intervalProcessingRunId)
            .append('\n')
            .append(intervals.resultHash());
    conditions.stream()
        .sorted(
            Comparator.comparingLong(ConditionEvidenceObservation::replaySequence)
                .thenComparing(ConditionEvidenceObservation::sourceObservedAt)
                .thenComparing(ConditionEvidenceObservation::sourceEventKey))
        .forEach(
            condition ->
                value
                    .append('\n')
                    .append(condition.replaySequence())
                    .append('|')
                    .append(condition.sourceObservedAt())
                    .append('|')
                    .append(condition.sourceEventKey())
                    .append('|')
                    .append(condition.level())
                    .append('|')
                    .append(condition.nativeCode())
                    .append('|')
                    .append(condition.message()));
    return EquipmentStateIntervalReport.sha256(value.toString());
  }

  private static String resultHash(List<DowntimeParetoEntry> entries) {
    StringBuilder value = new StringBuilder(RULE_VERSION);
    for (DowntimeParetoEntry entry : entries) {
      value
          .append('\n')
          .append(entry.rank())
          .append('|')
          .append(entry.state())
          .append('|')
          .append(entry.startedAt())
          .append('|')
          .append(entry.endedAt())
          .append('|')
          .append(entry.duration())
          .append('|')
          .append(entry.classification());
      entry.evidence().forEach(evidence -> value.append('|').append(evidence.sourceEventKey()));
    }
    return EquipmentStateIntervalReport.sha256(value.toString());
  }
}
