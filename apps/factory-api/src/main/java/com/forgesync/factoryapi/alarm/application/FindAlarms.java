package com.forgesync.factoryapi.alarm.application;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import java.util.List;
import java.util.UUID;

public interface FindAlarms {
  List<Alarm> find(String machineId, UUID replaySessionId, long throughReplaySequence);
}
