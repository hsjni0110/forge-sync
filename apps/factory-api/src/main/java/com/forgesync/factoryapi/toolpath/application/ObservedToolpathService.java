package com.forgesync.factoryapi.toolpath.application;

import com.forgesync.factoryapi.toolpath.domain.ObservedToolpath;
import com.forgesync.factoryapi.toolpath.domain.ObservedToolpathPolicy;
import java.util.UUID;

public final class ObservedToolpathService implements FindObservedToolpath {
  private final AxisPositionHistory history;
  private final ObservedToolpathPolicy policy;

  public ObservedToolpathService(AxisPositionHistory history, ObservedToolpathPolicy policy) {
    this.history = history;
    this.policy = policy;
  }

  @Override
  public ObservedToolpath find(
      String machineId,
      UUID replaySessionId,
      long startSequence,
      long endSequence,
      long throughSequence) {
    long effectiveEnd = Math.min(endSequence, throughSequence);
    return policy.build(
        history.read(machineId, replaySessionId, effectiveEnd), startSequence, effectiveEnd);
  }
}
