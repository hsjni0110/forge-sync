package com.forgesync.factoryapi.toolchange.application;

import com.forgesync.factoryapi.toolchange.domain.ToolChange;
import com.forgesync.factoryapi.toolchange.domain.ToolChangePolicy;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ToolChangeService implements FindToolChanges {
  private final ToolNumberHistory history;
  private final ToolChangePolicy policy;

  public ToolChangeService(ToolNumberHistory history, ToolChangePolicy policy) {
    this.history = Objects.requireNonNull(history);
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public List<ToolChange> find(String machineId, UUID replaySessionId, long throughReplaySequence) {
    return policy.detect(history.read(machineId, replaySessionId, throughReplaySequence));
  }
}
