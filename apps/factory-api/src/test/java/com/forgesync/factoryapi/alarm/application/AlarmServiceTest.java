package com.forgesync.factoryapi.alarm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import com.forgesync.factoryapi.alarm.domain.AlarmCandidate;
import com.forgesync.factoryapi.alarm.domain.AlarmSeverity;
import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlarmServiceTest {
  private static final UUID SESSION = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID ALARM_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final Instant NOW = Instant.parse("2026-09-13T01:00:00Z");

  @Test
  void findsAlarmsAtTheRequestedReplayCursor() {
    RecordingRepository repository = new RecordingRepository();
    repository.alarms.add(openAlarm());
    AlarmService service = service(repository);

    List<Alarm> result = service.find("Mazak01", SESSION, 42);

    assertThat(result).containsExactly(openAlarm());
    assertThat(repository.requestedCursor).isEqualTo(42);
    assertThatThrownBy(() -> service.find(" ", SESSION, 42))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.find("Mazak01", SESSION, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acknowledgesWithTheInjectedWallClockAndRejectsInvalidInput() {
    RecordingRepository repository = new RecordingRepository();
    AlarmService service = service(repository);

    Alarm acknowledged = service.acknowledge(ALARM_ID, 0, " 김 작업자 ");

    assertThat(acknowledged.acknowledgedBy()).isEqualTo("김 작업자");
    assertThat(acknowledged.acknowledgedAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> service.acknowledge(ALARM_ID, -1, "김 작업자"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.acknowledge(ALARM_ID, 0, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AlarmService service(RecordingRepository repository) {
    return new AlarmService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Alarm openAlarm() {
    var condition =
        new ConditionObservation(
            "Mazak01",
            SESSION,
            42,
            Instant.parse("2016-10-05T09:01:00Z"),
            "warning-42",
            "Mazak01-controller_2",
            "Mazak01-controller",
            "LOGIC_PROGRAM",
            "WARNING",
            "345",
            "ERROR(DOOR OPEN)");
    return Alarm.open(ALARM_ID, new AlarmCandidate(condition, AlarmSeverity.WARNING, "1.0.0"));
  }

  private static final class RecordingRepository implements AlarmRepository {
    private final List<Alarm> alarms = new ArrayList<>();
    private long requestedCursor = -1;

    @Override
    public List<Alarm> findThrough(
        String machineId, UUID replaySessionId, long throughReplaySequence) {
      requestedCursor = throughReplaySequence;
      return List.copyOf(alarms);
    }

    @Override
    public Alarm acknowledge(
        UUID alarmId, long expectedRevision, String operatorName, Instant acknowledgedAt) {
      return openAlarm().acknowledge(operatorName, acknowledgedAt);
    }
  }
}
