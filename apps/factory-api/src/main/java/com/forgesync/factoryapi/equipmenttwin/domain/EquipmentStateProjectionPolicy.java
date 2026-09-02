package com.forgesync.factoryapi.equipmenttwin.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class EquipmentStateProjectionPolicy {

  public EquipmentState project(Collection<StateObservation> latestObservations) {
    Objects.requireNonNull(latestObservations, "latestObservations");
    if (latestObservations.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("latestObservations must not contain null");
    }
    return new EquipmentState(
        connectivityOf(latestObservations),
        executionOf(latestObservations),
        healthOf(latestObservations));
  }

  private static ConnectivityState connectivityOf(Collection<StateObservation> latestObservations) {
    return latestObservations.stream()
            .anyMatch(
                observation -> observation.availability() == ObservationAvailability.AVAILABLE)
        ? ConnectivityState.ONLINE
        : ConnectivityState.UNKNOWN;
  }

  private static ExecutionState executionOf(Collection<StateObservation> latestObservations) {
    Set<ExecutionState> states = EnumSet.noneOf(ExecutionState.class);
    boolean hasUnavailableExecution = false;
    for (StateObservation observation : latestObservations) {
      if (observation.kind() != StateObservationKind.EVENT
          || !observation.semanticType().equals("EXECUTION")) {
        continue;
      }
      if (observation.availability() == ObservationAvailability.UNAVAILABLE) {
        hasUnavailableExecution = true;
        continue;
      }
      states.add(parseExecution(observation.value()));
    }
    states.remove(ExecutionState.UNKNOWN);
    if (hasUnavailableExecution || states.size() != 1) {
      return ExecutionState.UNKNOWN;
    }
    return states.iterator().next();
  }

  private static ExecutionState parseExecution(String value) {
    try {
      return ExecutionState.valueOf(value);
    } catch (IllegalArgumentException exception) {
      return ExecutionState.UNKNOWN;
    }
  }

  private static HealthState healthOf(Collection<StateObservation> latestObservations) {
    Set<String> levels =
        latestObservations.stream()
            .filter(observation -> observation.kind() == StateObservationKind.CONDITION)
            .map(
                observation ->
                    observation.availability() == ObservationAvailability.AVAILABLE
                        ? observation.value()
                        : "UNAVAILABLE")
            .collect(java.util.stream.Collectors.toSet());
    if (levels.contains("FAULT")) {
      return HealthState.FAULT;
    }
    if (levels.contains("WARNING")) {
      return HealthState.WARNING;
    }
    if (!levels.isEmpty() && levels.stream().allMatch("NORMAL"::equals)) {
      return HealthState.NORMAL;
    }
    return HealthState.UNKNOWN;
  }
}
