package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.PartCountObservation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PartCountObservationHistory {
  List<PartCountObservation> readPartCounts(
      String machineId,
      UUID replaySessionId,
      long throughReplaySequence,
      Instant observedFrom,
      Instant observedTo);
}
