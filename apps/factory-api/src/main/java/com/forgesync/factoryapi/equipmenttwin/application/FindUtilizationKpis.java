package com.forgesync.factoryapi.equipmenttwin.application;

public interface FindUtilizationKpis {
  UtilizationKpiProcessingResult findByProcessingRunId(String machineId, String processingRunId);
}
