package com.forgesync.factoryapi.equipmenttwin.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable segmentation result: the intervals, what they cover, and hashes that make the same
 * input under the same rule version reproduce byte for byte.
 */
public record EquipmentStateIntervalReport(
    String ruleVersion,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    Instant observedFrom,
    Instant observedTo,
    int inputObservationCount,
    String inputHash,
    String resultHash,
    List<EquipmentStateInterval> intervals,
    List<SignalCoverage> coverage) {

  public EquipmentStateIntervalReport {
    Objects.requireNonNull(ruleVersion, "ruleVersion");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(observedFrom, "observedFrom");
    Objects.requireNonNull(observedTo, "observedTo");
    if (observedTo.isBefore(observedFrom)) {
      throw new IllegalArgumentException("observedTo must not precede observedFrom");
    }
    intervals = List.copyOf(intervals);
    coverage = List.copyOf(coverage);
  }

  public static EquipmentStateIntervalReport of(
      String ruleVersion,
      List<StateSignalObservation> observations,
      List<EquipmentStateInterval> intervals) {
    if (observations.isEmpty()) {
      throw new IllegalArgumentException("A report requires at least one observation");
    }
    List<StateSignalObservation> ordered = new ArrayList<>(observations);
    ordered.sort(
        Comparator.comparingLong(StateSignalObservation::replaySequence)
            .thenComparing(StateSignalObservation::sourceObservedAt)
            .thenComparing(StateSignalObservation::sourceEventKey));
    StateSignalObservation first = ordered.get(0);
    StateSignalObservation last = ordered.get(ordered.size() - 1);
    Instant observedFrom = first.sourceObservedAt();
    Instant observedTo = last.sourceObservedAt();

    return new EquipmentStateIntervalReport(
        ruleVersion,
        first.machineId(),
        first.replaySessionId(),
        last.replaySequence(),
        observedFrom,
        observedTo,
        ordered.size(),
        inputHashOf(ruleVersion, ordered),
        sha256(canonicalResult(ruleVersion, intervals)),
        intervals,
        coverageOf(intervals, observedFrom));
  }

  public Duration observedRange() {
    return Duration.between(observedFrom, observedTo);
  }

  public SignalCoverage coverageOf(StateSignal signal) {
    return coverage.stream()
        .filter(entry -> entry.signal() == signal)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No coverage for signal " + signal));
  }

  private static List<SignalCoverage> coverageOf(
      List<EquipmentStateInterval> intervals, Instant observedFrom) {
    Map<StateSignal, List<EquipmentStateInterval>> bySignal = new EnumMap<>(StateSignal.class);
    for (EquipmentStateInterval interval : intervals) {
      bySignal.computeIfAbsent(interval.signal(), key -> new ArrayList<>()).add(interval);
    }
    List<SignalCoverage> coverage = new ArrayList<>();
    for (Map.Entry<StateSignal, List<EquipmentStateInterval>> entry : bySignal.entrySet()) {
      Duration closed = Duration.ZERO;
      Instant openSince = null;
      Instant firstStart = null;
      for (EquipmentStateInterval interval : entry.getValue()) {
        if (firstStart == null || interval.startedAt().isBefore(firstStart)) {
          firstStart = interval.startedAt();
        }
        if (interval.isOpen()) {
          openSince = interval.startedAt();
        } else {
          closed = closed.plus(interval.duration().orElseThrow());
        }
      }
      coverage.add(
          new SignalCoverage(
              entry.getKey(),
              closed,
              Duration.between(observedFrom, firstStart),
              openSince,
              entry.getValue().size()));
    }
    coverage.sort(Comparator.comparing(entry -> entry.signal().name()));
    return coverage;
  }

  /** The identity of an input set: same observations and rule version, same hash. */
  public static String inputHashOf(String ruleVersion, List<StateSignalObservation> observations) {
    List<StateSignalObservation> ordered = new ArrayList<>(observations);
    ordered.sort(
        Comparator.comparingLong(StateSignalObservation::replaySequence)
            .thenComparing(StateSignalObservation::sourceObservedAt)
            .thenComparing(StateSignalObservation::sourceEventKey));
    return sha256(canonicalInput(ruleVersion, ordered));
  }

  private static String canonicalInput(String ruleVersion, List<StateSignalObservation> ordered) {
    StringBuilder canonical = new StringBuilder(ruleVersion);
    for (StateSignalObservation observation : ordered) {
      canonical
          .append('\n')
          .append(observation.replaySequence())
          .append('|')
          .append(observation.sourceObservedAt())
          .append('|')
          .append(observation.sourceEventKey())
          .append('|')
          .append(observation.signal())
          .append('|')
          .append(observation.isAvailable() ? observation.value() : "UNAVAILABLE");
    }
    return canonical.toString();
  }

  private static String canonicalResult(
      String ruleVersion, List<EquipmentStateInterval> intervals) {
    StringBuilder canonical = new StringBuilder(ruleVersion);
    for (EquipmentStateInterval interval : intervals) {
      canonical
          .append('\n')
          .append(interval.signal())
          .append('|')
          .append(interval.value() == null ? "UNKNOWN" : interval.value())
          .append('|')
          .append(interval.startedAt())
          .append('|')
          .append(interval.endedAt() == null ? "OPEN" : interval.endedAt())
          .append('|')
          .append(interval.startEvidence().sourceEventKey())
          .append('|')
          .append(
              interval.endEvidence() == null ? "OPEN" : interval.endEvidence().sourceEventKey());
    }
    return canonical.toString();
  }

  public static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
