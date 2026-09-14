package com.forgesync.factoryapi.alarm.application;

import com.forgesync.factoryapi.alarm.domain.Alarm;
import java.util.UUID;

public interface AcknowledgeAlarm {
  Alarm acknowledge(UUID alarmId, long expectedRevision, String operatorName);
}
