package com.forgesync.factoryapi.processanalytics.application;

public interface ProcessCycleFeatures {
  CycleFeatureProcessingResult process(ProcessCycleFeaturesCommand command);
}
