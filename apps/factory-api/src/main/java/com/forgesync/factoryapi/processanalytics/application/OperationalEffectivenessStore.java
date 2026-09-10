package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessReport;
import java.time.Instant;
import java.util.Optional;

public interface OperationalEffectivenessStore {
  Optional<StoredOperationalEffectiveness> find(String processingRunId);

  boolean preserve(StoredOperationalEffectiveness processing);

  record StoredOperationalEffectiveness(
      String processingRunId, Instant createdAt, OperationalEffectivenessReport report) {}
}
