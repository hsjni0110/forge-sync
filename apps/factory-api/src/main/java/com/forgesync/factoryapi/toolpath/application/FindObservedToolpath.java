package com.forgesync.factoryapi.toolpath.application;

import com.forgesync.factoryapi.toolpath.domain.ObservedToolpath;
import java.util.UUID;

public interface FindObservedToolpath {
  ObservedToolpath find(
      String machineId,
      UUID replaySessionId,
      long startSequence,
      long endSequence,
      long throughSequence);
}
