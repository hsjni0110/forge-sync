package com.forgesync.factoryapi.equipmenttwin.application;

public interface GetOperationalTwinSnapshot {

  OperationalTwinSnapshot getSnapshot(String machineId);
}
