package com.forgesync.factoryapi.toolchange.application;

import com.forgesync.factoryapi.toolchange.domain.ToolChange;
import java.util.List;
import java.util.UUID;

public interface FindToolChanges {
  List<ToolChange> find(String machineId, UUID replaySessionId, long throughReplaySequence);
}
