package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentStateProjectionPolicyTest {

  private final EquipmentStateProjectionPolicy policy = new EquipmentStateProjectionPolicy();

  @Test
  void projectsOnlineActiveAndWorstKnownCondition() {
    EquipmentState state =
        policy.project(
            List.of(
                available(StateObservationKind.EVENT, "EXECUTION", "ACTIVE"),
                available(StateObservationKind.CONDITION, "SYSTEM", "NORMAL"),
                available(StateObservationKind.CONDITION, "TEMPERATURE", "WARNING")));

    assertThat(state)
        .isEqualTo(
            new EquipmentState(
                ConnectivityState.ONLINE, ExecutionState.ACTIVE, HealthState.WARNING));
  }

  @Test
  void doesNotInventOnlineNormalOrExecutionFromUnavailableInput() {
    EquipmentState state =
        policy.project(
            List.of(
                unavailable(StateObservationKind.EVENT, "EXECUTION"),
                unavailable(StateObservationKind.CONDITION, "SYSTEM")));

    assertThat(state)
        .isEqualTo(
            new EquipmentState(
                ConnectivityState.UNKNOWN, ExecutionState.UNKNOWN, HealthState.UNKNOWN));
  }

  @Test
  void faultAndWarningRemainVisibleWhenOtherConditionsAreUnavailable() {
    assertThat(
            policy
                .project(
                    List.of(
                        unavailable(StateObservationKind.CONDITION, "SYSTEM"),
                        available(StateObservationKind.CONDITION, "LOAD", "FAULT")))
                .health())
        .isEqualTo(HealthState.FAULT);
    assertThat(
            policy
                .project(
                    List.of(
                        unavailable(StateObservationKind.CONDITION, "SYSTEM"),
                        available(StateObservationKind.CONDITION, "LOAD", "WARNING")))
                .health())
        .isEqualTo(HealthState.WARNING);
  }

  @Test
  void reportsNormalOnlyWhenEveryCurrentConditionIsNormal() {
    assertThat(
            policy
                .project(
                    List.of(
                        available(StateObservationKind.CONDITION, "SYSTEM", "NORMAL"),
                        available(StateObservationKind.CONDITION, "LOAD", "NORMAL")))
                .health())
        .isEqualTo(HealthState.NORMAL);
    assertThat(
            policy
                .project(
                    List.of(
                        available(StateObservationKind.CONDITION, "SYSTEM", "NORMAL"),
                        unavailable(StateObservationKind.CONDITION, "LOAD")))
                .health())
        .isEqualTo(HealthState.UNKNOWN);
  }

  @Test
  void unsupportedOrConflictingExecutionIsUnknown() {
    assertThat(
            policy
                .project(List.of(available(StateObservationKind.EVENT, "EXECUTION", "PAUSED")))
                .execution())
        .isEqualTo(ExecutionState.UNKNOWN);
    assertThat(
            policy
                .project(
                    List.of(
                        available(StateObservationKind.EVENT, "EXECUTION", "ACTIVE"),
                        available(StateObservationKind.EVENT, "EXECUTION", "READY")))
                .execution())
        .isEqualTo(ExecutionState.UNKNOWN);
  }

  @Test
  void staleFreshnessMakesOnlineConnectivityExplicitlyStale() {
    EquipmentState state =
        new EquipmentState(ConnectivityState.ONLINE, ExecutionState.ACTIVE, HealthState.NORMAL);

    assertThat(state.effectiveConnectivity(FreshnessState.FRESH))
        .isEqualTo(ConnectivityState.ONLINE);
    assertThat(state.effectiveConnectivity(FreshnessState.STALE))
        .isEqualTo(ConnectivityState.STALE);
  }

  private static StateObservation available(
      StateObservationKind kind, String semanticType, String value) {
    return new StateObservation(kind, semanticType, ObservationAvailability.AVAILABLE, value);
  }

  private static StateObservation unavailable(StateObservationKind kind, String semanticType) {
    return new StateObservation(kind, semanticType, ObservationAvailability.UNAVAILABLE, null);
  }
}
