package com.forgesync.factoryapi.production.application;

import com.forgesync.factoryapi.production.domain.ObservedProductionContext;
import java.util.List;

public interface ObservedProductionContextSource {
  ProductionContextInput read(String machineId, String machiningRunProcessingRunId);

  record ProductionContextInput(
      String machineId,
      String replaySessionId,
      long throughReplaySequence,
      String machiningRunProcessingRunId,
      List<ObservedProductionContext.ProgramObservation> programs,
      List<ObservedProductionContext.ObservedMachiningRun> machiningRuns,
      List<ObservedProductionContext.PartCountObservation> partCounts) {}
}
