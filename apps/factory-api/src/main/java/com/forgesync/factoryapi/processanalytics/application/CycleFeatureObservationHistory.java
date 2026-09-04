package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.CycleObservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CycleFeatureObservationHistory {
  List<CycleObservation> readCycleObservations(
      String machineId, UUID replaySessionId, long throughReplaySequence, Instant beforeExclusive);
}
