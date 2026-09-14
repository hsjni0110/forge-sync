package com.forgesync.factoryapi.alarm.application;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlarmRepository {
  List<Alarm> findThrough(String machineId, UUID replaySessionId, long throughReplaySequence);

  Alarm acknowledge(
      UUID alarmId, long expectedRevision, String operatorName, Instant acknowledgedAt);
}
