package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import java.util.List;
import java.util.Optional;

public interface MachiningRunProjectionStore {
  boolean preserve(MachiningRunProcessingResult processingResult);

  Optional<MachiningRunProcessingResult> findProcessingRun(String processingRunId);

  List<MachiningRun> findMachiningRuns(String machineId, String processingRunId);
}
