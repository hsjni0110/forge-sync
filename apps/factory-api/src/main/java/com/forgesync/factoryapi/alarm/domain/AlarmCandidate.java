package com.forgesync.factoryapi.alarm.domain;

import java.util.Objects;

public record AlarmCandidate(
    ConditionObservation condition, AlarmSeverity severity, String ruleVersion) {

  public AlarmCandidate {
    Objects.requireNonNull(condition, "condition");
    Objects.requireNonNull(severity, "severity");
    if (ruleVersion == null || ruleVersion.isBlank()) {
      throw new IllegalArgumentException("ruleVersion must not be blank");
    }
  }
}
