package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendReport;
import java.util.List;

public interface ToolLoadTrendSource {
  ToolLoadTrendInput read(String machineId, String machiningRunProcessingRunId);

  record ToolLoadTrendInput(
      String machineId,
      String replaySessionId,
      long throughReplaySequence,
      String machiningRunProcessingRunId,
      List<ToolLoadTrendReport.MachiningRunInput> machiningRuns,
      List<ToolLoadTrendReport.ToolNumberObservation> toolNumbers,
      List<ToolLoadTrendReport.LoadObservation> loads) {}
}
