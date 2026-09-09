package com.forgesync.factoryapi.equipmenttwin.application;

public interface FindDowntimePareto {
  DowntimeParetoProcessingResult findByProcessingRunId(String machineId, String processingRunId);
}
