package com.forgesync.factoryapi.equipmenttwin.application;

public interface FindEquipmentStateIntervals {
  EquipmentStateIntervalProcessingResult findByProcessingRunId(
      String machineId, String processingRunId);
}
