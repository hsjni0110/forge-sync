package com.forgesync.factoryapi.alarm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConditionToAlarmPolicyTest {

  private final ConditionToAlarmPolicy policy = ConditionToAlarmPolicy.nistMazak01V1();

  @Test
  void opensWarningAndCriticalCandidatesOnlyForExplicitlyAllowedConditionCodes() {
    var warning = condition("WARNING", "345", "ERROR(DOOR OPEN)");
    var fault = condition("Mazak01-controller_3", "FAULT", "1101", "INTERFERE");

    assertThat(policy.alarmCandidate(warning))
        .get()
        .extracting(AlarmCandidate::severity)
        .isEqualTo(AlarmSeverity.WARNING);
    assertThat(policy.alarmCandidate(fault))
        .get()
        .extracting(AlarmCandidate::severity)
        .isEqualTo(AlarmSeverity.CRITICAL);
    assertThat(policy.alarmCandidate(condition("WARNING", "9999", "UNREVIEWED"))).isEmpty();
    assertThat(policy.alarmCandidate(condition("UNAVAILABLE", null, null))).isEmpty();
  }

  @Test
  void normalWithCodeResolvesOneAlarmAndNormalWithoutCodeResolvesTheDataItem() {
    assertThat(policy.resolutionScope(condition("NORMAL", "1105", null)))
        .isEqualTo(AlarmResolutionScope.NATIVE_CODE);
    assertThat(policy.resolutionScope(condition("NORMAL", null, null)))
        .isEqualTo(AlarmResolutionScope.SOURCE_DATA_ITEM);
    assertThat(policy.resolutionScope(condition("UNAVAILABLE", null, null)))
        .isEqualTo(AlarmResolutionScope.NONE);
  }

  private static ConditionObservation condition(String level, String nativeCode, String message) {
    return condition("Mazak01-controller_2", level, nativeCode, message);
  }

  private static ConditionObservation condition(
      String sourceDataItemId, String level, String nativeCode, String message) {
    return new ConditionObservation(
        "Mazak01",
        UUID.fromString("10000000-0000-4000-8000-000000000001"),
        42,
        Instant.parse("2016-10-05T09:01:00Z"),
        "condition-42",
        sourceDataItemId,
        "Mazak01-controller",
        "LOGIC_PROGRAM",
        level,
        nativeCode,
        message);
  }
}
