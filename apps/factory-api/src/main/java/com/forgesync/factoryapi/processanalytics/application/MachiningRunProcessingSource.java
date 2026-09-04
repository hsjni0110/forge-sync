package com.forgesync.factoryapi.processanalytics.application;

import java.util.Optional;

public interface MachiningRunProcessingSource {
  Optional<MachiningRunProcessingResult> findProcessingRun(String processingRunId);
}
