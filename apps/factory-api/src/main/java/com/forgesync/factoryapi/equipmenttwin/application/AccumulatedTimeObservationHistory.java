package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import java.util.List;
import java.util.UUID;

/** Reads uncorrected accumulated-time observations for one Replay Session and watermark. */
public interface AccumulatedTimeObservationHistory {
  List<AccumulatedTimeObservation> readAccumulatedTimes(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}
