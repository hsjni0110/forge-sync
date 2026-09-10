package com.forgesync.factoryapi.processanalytics.application;

public interface FindOperationalEffectiveness {
  OperationalEffectivenessProcessingResult find(String machineId, String processingRunId);
}
