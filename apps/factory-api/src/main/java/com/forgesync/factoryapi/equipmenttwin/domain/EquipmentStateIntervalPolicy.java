package com.forgesync.factoryapi.equipmenttwin.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reconstructs how long each state signal held each value, without adding or correcting anything
 * the source did not report (ADR-050).
 *
 * <p>An interval runs from the observation that reported a value to the next observation of the
 * SAME signal. There is no staleness threshold: MTConnect Events are reported on change, so silence
 * means the value held, and cutting a quiet stretch into UNKNOWN would invent a transition nobody
 * observed. Only an `UNAVAILABLE` observation opens an unknown interval, because only then did the
 * source say it did not know.
 *
 * <p>A signal whose last observation is never followed by another stays OPEN. Closing it at the end
 * of the stream would invent an end, so interval totals are smaller than the observed time range by
 * design; the caller reports that difference rather than hiding it.
 */
public final class EquipmentStateIntervalPolicy {

  public static final String RULE_VERSION = "1.0.0";

  public List<EquipmentStateInterval> segment(
      String ruleVersion, List<StateSignalObservation> observations) {
    requireSupportedVersion(ruleVersion);
    Objects.requireNonNull(observations, "observations");
    if (observations.isEmpty()) {
      return List.of();
    }
    List<StateSignalObservation> ordered = orderedCopy(observations);
    requireOneReplaySession(ordered);

    Map<StateSignal, OpenInterval> open = new EnumMap<>(StateSignal.class);
    List<EquipmentStateInterval> closed = new ArrayList<>();
    for (StateSignalObservation observation : ordered) {
      OpenInterval current = open.get(observation.signal());
      if (current != null && current.holdsSameValueAs(observation)) {
        // Re-reporting the value the signal already holds does not start a new interval.
        continue;
      }
      if (current != null) {
        closed.add(current.closeAt(observation));
      }
      open.put(observation.signal(), OpenInterval.startedBy(observation));
    }
    for (OpenInterval remaining : open.values()) {
      closed.add(remaining.leaveOpen());
    }
    closed.sort(
        Comparator.comparing(EquipmentStateInterval::startedAt)
            .thenComparing(interval -> interval.signal().name())
            .thenComparing(interval -> interval.startEvidence().replaySequence()));
    return List.copyOf(closed);
  }

  private static void requireSupportedVersion(String ruleVersion) {
    if (!RULE_VERSION.equals(ruleVersion)) {
      throw new IllegalArgumentException("Unsupported interval rule version: " + ruleVersion);
    }
  }

  private static List<StateSignalObservation> orderedCopy(
      List<StateSignalObservation> observations) {
    List<StateSignalObservation> ordered = new ArrayList<>(observations);
    ordered.sort(
        Comparator.comparingLong(StateSignalObservation::replaySequence)
            .thenComparing(StateSignalObservation::sourceObservedAt)
            .thenComparing(StateSignalObservation::sourceEventKey));
    return ordered;
  }

  private static void requireOneReplaySession(List<StateSignalObservation> ordered) {
    UUID session = ordered.get(0).replaySessionId();
    for (StateSignalObservation observation : ordered) {
      if (!session.equals(observation.replaySessionId())) {
        throw new IllegalArgumentException(
            "Intervals cannot span more than one Replay Session: " + session);
      }
    }
  }

  private record OpenInterval(StateSignalObservation opener) {

    static OpenInterval startedBy(StateSignalObservation observation) {
      return new OpenInterval(observation);
    }

    boolean holdsSameValueAs(StateSignalObservation observation) {
      return Objects.equals(valueOf(opener), valueOf(observation));
    }

    EquipmentStateInterval closeAt(StateSignalObservation closer) {
      return new EquipmentStateInterval(
          opener.signal(),
          valueOf(opener),
          opener.sourceObservedAt(),
          closer.sourceObservedAt(),
          IntervalBoundaryEvidence.of(opener),
          IntervalBoundaryEvidence.of(closer));
    }

    EquipmentStateInterval leaveOpen() {
      return new EquipmentStateInterval(
          opener.signal(),
          valueOf(opener),
          opener.sourceObservedAt(),
          null,
          IntervalBoundaryEvidence.of(opener),
          null);
    }

    private static String valueOf(StateSignalObservation observation) {
      return observation.isAvailable() ? observation.value() : null;
    }
  }
}
