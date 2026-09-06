package com.forgesync.factoryapi.toolpath.application;

import com.forgesync.factoryapi.toolpath.domain.AxisPositionObservation;
import java.util.List;
import java.util.UUID;

public interface AxisPositionHistory {
  List<AxisPositionObservation> read(String machineId, UUID replaySessionId, long throughSequence);
}
