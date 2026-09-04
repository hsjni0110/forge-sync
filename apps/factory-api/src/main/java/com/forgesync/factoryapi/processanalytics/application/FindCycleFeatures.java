package com.forgesync.factoryapi.processanalytics.application;

public interface FindCycleFeatures {
  CycleFeatureProcessingResult find(String machineId, String featureProcessingRunId);
}
