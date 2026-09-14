package com.forgesync.factoryapi.alarm.application;

import java.util.UUID;

public final class AlarmNotFoundException extends RuntimeException {
  public AlarmNotFoundException(UUID alarmId) {
    super("Alarm not found: " + alarmId);
  }
}
