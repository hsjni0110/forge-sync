package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.Optional;

public interface TwinProjectionReader {

  Optional<LoadedTwinProjection> findByMachineId(String machineId);
}
