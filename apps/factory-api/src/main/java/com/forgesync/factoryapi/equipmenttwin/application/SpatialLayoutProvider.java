package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.Optional;

public interface SpatialLayoutProvider {
  Optional<SpatialLayout> findByMachineId(String machineId);
}
