package com.forgesync.factoryapi.alarm.application;

import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import java.time.Instant;

public interface AlarmObservationParticipant {
  void project(ConditionObservation condition, Instant recordedAt);

  static AlarmObservationParticipant noOp() {
    return (condition, recordedAt) -> {};
  }
}
