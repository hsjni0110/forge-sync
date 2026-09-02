package com.forgesync.factoryapi.equipmenttwin.domain;

import java.util.Objects;

public record EquipmentState(
    ConnectivityState connectivity, ExecutionState execution, HealthState health) {

  public EquipmentState {
    Objects.requireNonNull(connectivity, "connectivity");
    Objects.requireNonNull(execution, "execution");
    Objects.requireNonNull(health, "health");
  }

  public ConnectivityState effectiveConnectivity(FreshnessState freshness) {
    Objects.requireNonNull(freshness, "freshness");
    if (connectivity == ConnectivityState.ONLINE && freshness == FreshnessState.STALE) {
      return ConnectivityState.STALE;
    }
    return connectivity;
  }
}
