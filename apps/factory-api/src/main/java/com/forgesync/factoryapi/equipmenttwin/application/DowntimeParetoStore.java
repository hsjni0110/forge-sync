package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import java.time.Instant;
import java.util.Optional;

public interface DowntimeParetoStore {
  Optional<StoredDowntimePareto> findProcessingRun(String processingRunId);

  boolean preserve(StoredDowntimePareto processing);

  record StoredDowntimePareto(
      String processingRunId, Instant createdAt, DowntimeParetoReport report) {}
}
