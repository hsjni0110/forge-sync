package com.forgesync.factoryapi.alarm.application;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AlarmService implements FindAlarms, AcknowledgeAlarm {
  private final AlarmRepository repository;
  private final Clock clock;

  public AlarmService(AlarmRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<Alarm> find(String machineId, UUID replaySessionId, long throughReplaySequence) {
    requireText(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (throughReplaySequence < 0) {
      throw new IllegalArgumentException("throughReplaySequence must not be negative");
    }
    return repository.findThrough(machineId, replaySessionId, throughReplaySequence);
  }

  @Override
  public Alarm acknowledge(UUID alarmId, long expectedRevision, String operatorName) {
    Objects.requireNonNull(alarmId, "alarmId");
    if (expectedRevision < 0) {
      throw new IllegalArgumentException("expectedRevision must not be negative");
    }
    requireText(operatorName, "operatorName");
    return repository.acknowledge(alarmId, expectedRevision, operatorName.trim(), clock.instant());
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
