package com.forgesync.factoryapi.alarm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlarmTest {

  @Test
  void recordsNamedAcknowledgementAndKeepsRepeatedAcknowledgementIdempotent() {
    Alarm open = openAlarm();
    Instant acknowledgedAt = Instant.parse("2026-09-12T01:00:00Z");

    Alarm acknowledged = open.acknowledge("김 작업자", acknowledgedAt);

    assertThat(acknowledged.status()).isEqualTo(AlarmStatus.ACKNOWLEDGED);
    assertThat(acknowledged.acknowledgedBy()).isEqualTo("김 작업자");
    assertThat(acknowledged.acknowledgedAt()).isEqualTo(acknowledgedAt);
    assertThat(acknowledged.revision()).isEqualTo(1);
    assertThat(acknowledged.acknowledge("다른 이름", acknowledgedAt.plusSeconds(1)))
        .isSameAs(acknowledged);
    assertThatThrownBy(() -> open.acknowledge("  ", acknowledgedAt))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvesOpenOrAcknowledgedAlarmAndRejectsAcknowledgementAfterResolution() {
    Instant resolvedAt = Instant.parse("2016-10-05T09:02:00Z");
    Alarm resolvedOpen = openAlarm().resolve("normal-43", 43, resolvedAt);
    Alarm resolvedAcknowledged =
        openAlarm()
            .acknowledge("김 작업자", Instant.parse("2026-09-12T01:00:00Z"))
            .resolve("normal-43", 43, resolvedAt);

    assertThat(resolvedOpen.status()).isEqualTo(AlarmStatus.RESOLVED);
    assertThat(resolvedOpen.resolvedBySourceEventKey()).isEqualTo("normal-43");
    assertThat(resolvedOpen.resolvedAt()).isEqualTo(resolvedAt);
    assertThat(resolvedOpen.resolvedReplaySequence()).isEqualTo(43);
    assertThat(resolvedAcknowledged.status()).isEqualTo(AlarmStatus.RESOLVED);
    assertThat(resolvedAcknowledged.revision()).isEqualTo(2);
    assertThat(resolvedOpen.resolve("normal-44", 44, resolvedAt.plusSeconds(1)))
        .isSameAs(resolvedOpen);
    assertThatThrownBy(
            () -> resolvedOpen.acknowledge("김 작업자", Instant.parse("2026-09-12T01:00:00Z")))
        .isInstanceOf(InvalidAlarmTransitionException.class);
    assertThatThrownBy(
            () -> openAlarm().resolve("late-normal", 41, Instant.parse("2016-10-05T09:00:59Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Alarm openAlarm() {
    ConditionObservation condition =
        new ConditionObservation(
            "Mazak01",
            UUID.fromString("10000000-0000-4000-8000-000000000001"),
            42,
            Instant.parse("2016-10-05T09:01:00Z"),
            "warning-42",
            "Mazak01-controller_2",
            "Mazak01-controller",
            "LOGIC_PROGRAM",
            "WARNING",
            "345",
            "ERROR(DOOR OPEN)");
    return Alarm.open(
        UUID.fromString("20000000-0000-4000-8000-000000000001"),
        new AlarmCandidate(condition, AlarmSeverity.WARNING, "1.0.0"));
  }
}
