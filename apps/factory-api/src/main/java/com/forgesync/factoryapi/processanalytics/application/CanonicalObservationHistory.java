package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.ProcessObservation;
import java.util.List;
import java.util.UUID;

public interface CanonicalObservationHistory {
  List<ProcessObservation> readRelevantObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}
