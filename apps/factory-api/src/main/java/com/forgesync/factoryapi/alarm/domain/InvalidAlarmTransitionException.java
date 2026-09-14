package com.forgesync.factoryapi.alarm.domain;

public final class InvalidAlarmTransitionException extends RuntimeException {
  public InvalidAlarmTransitionException(String message) {
    super(message);
  }
}
