package com.forgesync.factoryapi.processanalytics.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface UtilizationEvidenceSource {
  UtilizationEvidence find(String machineId, String processingRunId);

  record UtilizationEvidence(
      String processingRunId,
      UUID replaySessionId,
      long throughReplaySequence,
      Instant observedFrom,
      Instant observedTo,
      String resultHash,
      BigDecimal activePercent,
      boolean isAvailable) {}
}
