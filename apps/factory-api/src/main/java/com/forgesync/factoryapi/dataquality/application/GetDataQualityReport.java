package com.forgesync.factoryapi.dataquality.application;

import java.util.UUID;

public interface GetDataQualityReport {
  DataQualityReport getSource(String machineId);

  DataQualityReport getScoped(String machineId, UUID replaySessionId, long throughReplaySequence);
}
