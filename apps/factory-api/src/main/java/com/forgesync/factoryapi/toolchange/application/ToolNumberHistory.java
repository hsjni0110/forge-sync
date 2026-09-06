package com.forgesync.factoryapi.toolchange.application;

import com.forgesync.factoryapi.toolchange.domain.ToolNumberObservation;
import java.util.List;
import java.util.UUID;

public interface ToolNumberHistory {
  List<ToolNumberObservation> read(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}
