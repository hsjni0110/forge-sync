package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentStateIntervalReportTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private final EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();

  @Test
  void reportsTheSameBoundariesAndHashForTheSameInputAndRuleVersion() {
    List<StateSignalObservation> observations = executionStream();

    EquipmentStateIntervalReport first =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations,
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
    EquipmentStateIntervalReport shuffled =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations.reversed(),
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations.reversed()));

    assertThat(shuffled.resultHash()).isEqualTo(first.resultHash());
    assertThat(shuffled.inputHash()).isEqualTo(first.inputHash());
    assertThat(first.resultHash()).matches("^sha256:[0-9a-f]{64}$");
  }

  @Test
  void changesTheResultHashWhenABoundaryMoves() {
    List<StateSignalObservation> observations = executionStream();
    List<StateSignalObservation> moved =
        List.of(
            observations.get(0),
            observation(1, START.plusSeconds(91), StateSignal.EXECUTION, "ACTIVE"),
            observations.get(2));

    EquipmentStateIntervalReport original =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations,
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
    EquipmentStateIntervalReport shifted =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            moved,
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, moved));

    assertThat(shifted.resultHash()).isNotEqualTo(original.resultHash());
  }

  @Test
  void accountsForEverySeconduncoveredBySignalWithoutExceedingTheObservedRange() {
    List<StateSignalObservation> observations = executionStream();

    EquipmentStateIntervalReport report =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations,
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
    SignalCoverage coverage = report.coverageOf(StateSignal.EXECUTION);

    // 0s READY, 90s ACTIVE, then STOPPED stays open at 240s. The range ends at the last
    // observation of any signal, so the open interval contributes nothing measurable.
    assertThat(report.observedRange()).isEqualTo(Duration.ofSeconds(240));
    assertThat(coverage.closedDuration()).isEqualTo(Duration.ofSeconds(240));
    assertThat(coverage.leadingUnobserved()).isEqualTo(Duration.ZERO);
    assertThat(coverage.openSince()).isEqualTo(START.plusSeconds(240));
    assertThat(coverage.closedDuration()).isLessThanOrEqualTo(report.observedRange());
  }

  @Test
  void countsTimeBeforeASignalIsFirstObservedAsUnobservedRatherThanAsAState() {
    List<StateSignalObservation> observations =
        List.of(
            observation(0, START, StateSignal.EXECUTION, "ACTIVE"),
            observation(1, START.plusSeconds(600), StateSignal.MODE, "AUTOMATIC"),
            observation(2, START.plusSeconds(900), StateSignal.MODE, "MANUAL"));

    EquipmentStateIntervalReport report =
        EquipmentStateIntervalReport.of(
            EquipmentStateIntervalPolicy.RULE_VERSION,
            observations,
            policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
    SignalCoverage mode = report.coverageOf(StateSignal.CONTROLLER_MODE);

    assertThat(mode.leadingUnobserved()).isEqualTo(Duration.ofSeconds(600));
    assertThat(mode.closedDuration()).isEqualTo(Duration.ofSeconds(300));
    assertThat(
            mode.leadingUnobserved()
                .plus(mode.closedDuration())
                .plus(Duration.between(mode.openSince(), report.observedTo())))
        .isEqualTo(report.observedRange());
  }

  private static List<StateSignalObservation> executionStream() {
    return List.of(
        observation(0, START, StateSignal.EXECUTION, "READY"),
        observation(1, START.plusSeconds(90), StateSignal.EXECUTION, "ACTIVE"),
        observation(2, START.plusSeconds(240), StateSignal.EXECUTION, "STOPPED"));
  }

  private static StateSignalObservation observation(
      long sequence, Instant at, StateSignal signal, String value) {
    return new StateSignalObservation(
        "Mazak01", SESSION, sequence, at, "event-" + sequence, signal, true, value);
  }
}
