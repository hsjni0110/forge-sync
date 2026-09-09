package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DowntimeParetoPolicyTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void ranksClosedDowntimeDeterministicallyAndKeepsEveryConcurrentEvidenceWithoutClaimingCause() {
    EquipmentStateInterval stopped = interval(StateSignal.EXECUTION, "STOPPED", 0, 100, 10);
    EquipmentStateInterval interrupted = interval(StateSignal.EXECUTION, "FEED_HOLD", 200, 260, 20);
    EquipmentStateInterval unknown = interval(StateSignal.EXECUTION, null, 300, 400, 30);
    EquipmentStateInterval active = interval(StateSignal.EXECUTION, "ACTIVE", 400, 500, 40);
    EquipmentStateInterval estop = interval(StateSignal.EMERGENCY_STOP, "TRIGGERED", 20, 80, 50);
    EquipmentStateInterval modeBefore =
        interval(StateSignal.CONTROLLER_MODE, "AUTOMATIC", -10, 30, 60);
    EquipmentStateInterval modeChange =
        interval(StateSignal.CONTROLLER_MODE, "MANUAL", 30, 150, 61);
    EquipmentStateIntervalReport intervals =
        report(List.of(stopped, interrupted, unknown, active, estop, modeBefore, modeChange));
    List<ConditionEvidenceObservation> conditions =
        List.of(
            condition(70, 70, "WARNING", "DOOR_OPEN"),
            condition(71, 150, "FAULT", "OUTSIDE_INTERVAL"));

    DowntimeParetoReport report =
        new DowntimeParetoPolicy()
            .rank("sha256:" + "b".repeat(64), "sha256:" + "d".repeat(64), intervals, conditions);

    assertThat(report.totalDowntimeSeconds().toString()).isEqualTo("260.000000");
    assertThat(report.entries())
        .extracting(DowntimeParetoEntry::state)
        .containsExactly(
            UtilizationState.STOPPED, UtilizationState.UNKNOWN, UtilizationState.INTERRUPTED);
    assertThat(report.entries()).extracting(DowntimeParetoEntry::rank).containsExactly(1, 2, 3);
    assertThat(report.entries().get(0).cumulativeRatioPercent().toString()).isEqualTo("38.461538");
    assertThat(report.entries().get(2).cumulativeRatioPercent().toString()).isEqualTo("100.000000");
    assertThat(report.entries().get(0).classification())
        .isEqualTo(DowntimeReasonClassification.CONCURRENT_EVIDENCE);
    assertThat(report.entries().get(0).evidence())
        .extracting(DowntimeEvidence::kind)
        .containsExactly(
            DowntimeEvidenceKind.ESTOP_OVERLAP,
            DowntimeEvidenceKind.MODE_CHANGE,
            DowntimeEvidenceKind.CONDITION_OBSERVATION);
    assertThat(report.entries().get(1).classification())
        .isEqualTo(DowntimeReasonClassification.UNCONFIRMED_REASON);
    assertThat(report.entries().get(1).evidence()).isEmpty();
  }

  @Test
  void excludesConditionsOutsideTheHalfOpenDowntimeBoundary() {
    EquipmentStateInterval stopped = interval(StateSignal.EXECUTION, "STOPPED", 10, 20, 10);

    DowntimeParetoEntry entry =
        new DowntimeParetoPolicy()
            .rank(
                "sha256:" + "b".repeat(64),
                "sha256:" + "d".repeat(64),
                report(List.of(stopped)),
                List.of(
                    condition(1, 9, "WARNING", "BEFORE"),
                    condition(2, 10, "WARNING", "AT_START"),
                    condition(3, 20, "FAULT", "AT_END")))
            .entries()
            .getFirst();

    assertThat(entry.evidence()).extracting(DowntimeEvidence::message).containsExactly("AT_START");
  }

  private static EquipmentStateIntervalReport report(List<EquipmentStateInterval> intervals) {
    return new EquipmentStateIntervalReport(
        "1.0.0",
        "Mazak01",
        SESSION,
        100,
        START.minusSeconds(10),
        START.plusSeconds(500),
        20,
        "sha256:" + "a".repeat(64),
        "sha256:" + "c".repeat(64),
        intervals,
        List.of());
  }

  private static EquipmentStateInterval interval(
      StateSignal signal, String value, long startSeconds, long endSeconds, long sequence) {
    return new EquipmentStateInterval(
        signal,
        value,
        START.plusSeconds(startSeconds),
        START.plusSeconds(endSeconds),
        new IntervalBoundaryEvidence(
            sequence, START.plusSeconds(startSeconds), "start-" + sequence),
        new IntervalBoundaryEvidence(
            sequence + 1, START.plusSeconds(endSeconds), "end-" + sequence));
  }

  private static ConditionEvidenceObservation condition(
      long sequence, long seconds, String level, String message) {
    return new ConditionEvidenceObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(seconds),
        "condition-" + sequence,
        "Mazak01-controller",
        "SYSTEM",
        level,
        "CODE-" + sequence,
        message);
  }
}
