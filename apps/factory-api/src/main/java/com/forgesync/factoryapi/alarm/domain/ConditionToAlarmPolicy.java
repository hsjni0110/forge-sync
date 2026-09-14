package com.forgesync.factoryapi.alarm.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConditionToAlarmPolicy {

  public static final String RULE_VERSION = "1.0.0";
  private static final Map<String, Set<String>> ALLOWED_NATIVE_CODES =
      Map.of(
          "Mazak01-controller_2", Set.of("345"),
          "Mazak01-controller_3", Set.of("401", "406", "442", "468", "1101", "1105"));

  public static ConditionToAlarmPolicy nistMazak01V1() {
    return new ConditionToAlarmPolicy();
  }

  public Optional<AlarmCandidate> alarmCandidate(ConditionObservation condition) {
    if (!Set.of("WARNING", "FAULT").contains(condition.level())
        || condition.nativeCode() == null
        || !ALLOWED_NATIVE_CODES
            .getOrDefault(condition.sourceDataItemId(), Set.of())
            .contains(condition.nativeCode())) {
      return Optional.empty();
    }
    AlarmSeverity severity =
        condition.level().equals("FAULT") ? AlarmSeverity.CRITICAL : AlarmSeverity.WARNING;
    return Optional.of(new AlarmCandidate(condition, severity, RULE_VERSION));
  }

  public AlarmResolutionScope resolutionScope(ConditionObservation condition) {
    if (!condition.level().equals("NORMAL")) return AlarmResolutionScope.NONE;
    return condition.nativeCode() == null || condition.nativeCode().isBlank()
        ? AlarmResolutionScope.SOURCE_DATA_ITEM
        : AlarmResolutionScope.NATIVE_CODE;
  }
}
