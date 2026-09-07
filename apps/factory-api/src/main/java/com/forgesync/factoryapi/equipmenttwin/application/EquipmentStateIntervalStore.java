package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import java.util.Optional;

/** Keeps every processing result; a later one never edits an earlier one. */
public interface EquipmentStateIntervalStore {

  Optional<StoredIntervalProcessing> findProcessingRun(String processingRunId);

  /**
   * @return false when this processing run was already stored by a concurrent writer.
   */
  boolean preserve(StoredIntervalProcessing processing);

  Optional<StoredIntervalProcessing> findLatestForSession(
      String machineId, java.util.UUID replaySessionId);

  record StoredIntervalProcessing(
      String processingRunId, java.time.Instant createdAt, EquipmentStateIntervalReport report) {}
}
