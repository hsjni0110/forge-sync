package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import java.util.List;
import java.util.UUID;

/** Reads the state-signal observations one Replay Session has accumulated so far. */
public interface StateIntervalObservationHistory {
  List<StateSignalObservation> readStateObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}
