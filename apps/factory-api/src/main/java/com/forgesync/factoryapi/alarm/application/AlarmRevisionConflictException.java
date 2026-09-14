package com.forgesync.factoryapi.alarm.application;

public final class AlarmRevisionConflictException extends RuntimeException {
  public AlarmRevisionConflictException() {
    super("Alarm revision does not match");
  }
}
