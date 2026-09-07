package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.application.EquipmentStateIntervalStore.StoredIntervalProcessing;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateInterval;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Turns the state observations of one Replay Session into intervals and keeps the result. Running
 * it again on the same input returns the stored result rather than recomputing meaning; running it
 * on a longer input writes a NEW processing run and leaves the earlier one exactly as it was
 * (ADR-050).
 */
public final class EquipmentStateIntervalService
    implements ProjectEquipmentStateIntervals, FindEquipmentStateIntervals {

  private final StateIntervalObservationHistory observationHistory;
  private final EquipmentStateIntervalStore store;
  private final EquipmentStateIntervalPolicy policy;
  private final Clock clock;

  public EquipmentStateIntervalService(
      StateIntervalObservationHistory observationHistory,
      EquipmentStateIntervalStore store,
      EquipmentStateIntervalPolicy policy,
      Clock clock) {
    this.observationHistory = Objects.requireNonNull(observationHistory);
    this.store = Objects.requireNonNull(store);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public EquipmentStateIntervalProcessingResult project(EquipmentStateIntervalCommand command) {
    List<StateSignalObservation> observations =
        observationHistory.readStateObservations(
            command.machineId(), command.replaySessionId(), command.throughReplaySequence());
    if (observations.isEmpty()) {
      throw new EquipmentStateIntervalNotFoundException(command.machineId());
    }

    String processingRunId =
        processingRunId(
            command, EquipmentStateIntervalReport.inputHashOf(command.ruleVersion(), observations));
    var existing = store.findProcessingRun(processingRunId);
    if (existing.isPresent()) {
      return asResult(existing.get()).asExisting();
    }

    List<EquipmentStateInterval> intervals = policy.segment(command.ruleVersion(), observations);
    EquipmentStateIntervalReport report =
        EquipmentStateIntervalReport.of(command.ruleVersion(), observations, intervals);
    Instant createdAt = clock.instant();
    boolean created =
        store.preserve(new StoredIntervalProcessing(processingRunId, createdAt, report));
    return created
        ? new EquipmentStateIntervalProcessingResult(processingRunId, createdAt, true, report)
        : asResult(store.findProcessingRun(processingRunId).orElseThrow()).asExisting();
  }

  @Override
  public EquipmentStateIntervalProcessingResult findByProcessingRunId(
      String machineId, String processingRunId) {
    StoredIntervalProcessing stored =
        store
            .findProcessingRun(processingRunId)
            .filter(processing -> processing.report().machineId().equals(machineId))
            .orElseThrow(() -> new EquipmentStateIntervalNotFoundException(processingRunId));
    return asResult(stored).asExisting();
  }

  private static EquipmentStateIntervalProcessingResult asResult(StoredIntervalProcessing stored) {
    return new EquipmentStateIntervalProcessingResult(
        stored.processingRunId(), stored.createdAt(), true, stored.report());
  }

  private static String processingRunId(EquipmentStateIntervalCommand command, String inputHash) {
    return EquipmentStateIntervalReport.sha256(
        command.machineId()
            + "\n"
            + command.replaySessionId()
            + "\n"
            + command.throughReplaySequence()
            + "\n"
            + command.ruleVersion()
            + "\n"
            + inputHash);
  }
}
