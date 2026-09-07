package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentStateIntervalPolicyTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private final EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();

  @Test
  void closesAnIntervalOnTheNextObservationOfTheSameSignal() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "READY"),
                observation(1, START.plusSeconds(90), StateSignal.EXECUTION, "ACTIVE"),
                observation(2, START.plusSeconds(240), StateSignal.EXECUTION, "STOPPED")));

    assertThat(intervals).hasSize(3);
    assertThat(intervals.get(0).value()).isEqualTo("READY");
    assertThat(intervals.get(0).endedAt()).isEqualTo(START.plusSeconds(90));
    assertThat(intervals.get(0).duration()).contains(Duration.ofSeconds(90));
    assertThat(intervals.get(1).value()).isEqualTo("ACTIVE");
    assertThat(intervals.get(1).duration()).contains(Duration.ofSeconds(150));
  }

  @Test
  void leavesTheLastIntervalOpenRatherThanInventingAnEnd() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "READY"),
                observation(1, START.plusSeconds(90), StateSignal.EXECUTION, "ACTIVE")));

    EquipmentStateInterval open = intervals.get(intervals.size() - 1);
    assertThat(open.endedAt()).isNull();
    assertThat(open.duration()).isEmpty();
    assertThat(open.isOpen()).isTrue();
    assertThat(open.endEvidence()).isNull();
  }

  @Test
  void opensAnUnknownIntervalOnlyWhereTheSourceReportsUnavailable() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                unavailable(0, START, StateSignal.EXECUTION),
                observation(1, START.plusSeconds(11621), StateSignal.EXECUTION, "READY")));

    assertThat(intervals.get(0).value()).isNull();
    assertThat(intervals.get(0).isUnknown()).isTrue();
    assertThat(intervals.get(0).duration()).contains(Duration.ofSeconds(11621));
  }

  @Test
  void carriesALongSilenceInsideOneIntervalInsteadOfCuttingIt() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "ACTIVE"),
                observation(1, START.plusSeconds(7200), StateSignal.EXECUTION, "STOPPED")));

    assertThat(intervals).hasSize(2);
    assertThat(intervals.get(0).duration()).contains(Duration.ofHours(2));
    assertThat(intervals.get(0).isUnknown()).isFalse();
  }

  @Test
  void keepsEachSignalOnItsOwnTimeline() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "ACTIVE"),
                observation(1, START.plusSeconds(30), StateSignal.MODE, "AUTOMATIC"),
                observation(2, START.plusSeconds(60), StateSignal.EXECUTION, "STOPPED"),
                observation(3, START.plusSeconds(90), StateSignal.ESTOP, "ARMED")));

    assertThat(intervals).filteredOn(i -> i.signal() == StateSignal.EXECUTION).hasSize(2);
    assertThat(intervals).filteredOn(i -> i.signal() == StateSignal.MODE).hasSize(1);
    assertThat(intervals).filteredOn(i -> i.signal() == StateSignal.ESTOP).hasSize(1);
    EquipmentStateInterval execution =
        intervals.stream()
            .filter(i -> i.signal() == StateSignal.EXECUTION)
            .findFirst()
            .orElseThrow();
    assertThat(execution.endedAt()).isEqualTo(START.plusSeconds(60));
  }

  @Test
  void repeatingTheSameValueDoesNotOpenANewInterval() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "ACTIVE"),
                observation(1, START.plusSeconds(30), StateSignal.EXECUTION, "ACTIVE"),
                observation(2, START.plusSeconds(60), StateSignal.EXECUTION, "STOPPED")));

    assertThat(intervals).filteredOn(i -> i.signal() == StateSignal.EXECUTION).hasSize(2);
    assertThat(intervals.get(0).startedAt()).isEqualTo(START);
    assertThat(intervals.get(0).endedAt()).isEqualTo(START.plusSeconds(60));
  }

  @Test
  void tracesEveryBoundaryBackToTheObservationThatCausedIt() {
    List<EquipmentStateInterval> intervals =
        policy.segment(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            List.of(
                observation(0, START, StateSignal.EXECUTION, "READY"),
                observation(7, START.plusSeconds(90), StateSignal.EXECUTION, "ACTIVE")));

    EquipmentStateInterval closed = intervals.get(0);
    assertThat(closed.startEvidence().replaySequence()).isEqualTo(0);
    assertThat(closed.endEvidence().replaySequence()).isEqualTo(7);
    assertThat(closed.startEvidence().sourceEventKey()).isEqualTo("event-0");
  }

  @Test
  void refusesObservationsFromMoreThanOneReplaySession() {
    StateSignalObservation other =
        new StateSignalObservation(
            "Mazak01",
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            1,
            START.plusSeconds(30),
            "event-other",
            StateSignal.EXECUTION,
            true,
            "ACTIVE");

    assertThatThrownBy(
            () ->
                policy.segment(
                    EquipmentStateIntervalPolicy.RULE_VERSION,
                    List.of(observation(0, START, StateSignal.EXECUTION, "READY"), other)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Replay Session");
  }

  @Test
  void refusesAnUnsupportedRuleVersion() {
    assertThatThrownBy(() -> policy.segment("9.9.9", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("9.9.9");
  }

  private static StateSignalObservation observation(
      long sequence, Instant at, StateSignal signal, String value) {
    return new StateSignalObservation(
        "Mazak01", SESSION, sequence, at, "event-" + sequence, signal, true, value);
  }

  private static StateSignalObservation unavailable(long sequence, Instant at, StateSignal signal) {
    return new StateSignalObservation(
        "Mazak01", SESSION, sequence, at, "event-" + sequence, signal, false, null);
  }
}
