package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiReport;
import java.time.Instant;
import java.util.Optional;

/** Preserves immutable utilization processing results. */
public interface UtilizationKpiStore {
  Optional<StoredUtilizationKpi> findProcessingRun(String processingRunId);

  boolean preserve(StoredUtilizationKpi processing);

  record StoredUtilizationKpi(
      String processingRunId, Instant createdAt, UtilizationKpiReport report) {}
}
