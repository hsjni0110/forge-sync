package com.forgesync.factoryapi.processanalytics.application;

public interface ProjectOperationalEffectiveness {
  OperationalEffectivenessProcessingResult project(OperationalEffectivenessCommand command);
}
