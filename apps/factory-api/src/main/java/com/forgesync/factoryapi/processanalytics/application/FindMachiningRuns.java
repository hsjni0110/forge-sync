package com.forgesync.factoryapi.processanalytics.application;

public interface FindMachiningRuns {
  MachiningRunProcessingResult find(String machineId, String processingRunId);
}
