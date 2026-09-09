package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.ConditionEvidenceObservation;
import java.util.List;
import java.util.UUID;

public interface ConditionEvidenceHistory {
  List<ConditionEvidenceObservation> readAttentionConditions(
      String machineId, UUID replaySessionId, long throughReplaySequence);
}
