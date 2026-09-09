package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiStore.StoredUtilizationKpi;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiReport;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class UtilizationKpiService implements ProjectUtilizationKpis, FindUtilizationKpis {

  private final FindEquipmentStateIntervals intervalFinder;
  private final AccumulatedTimeObservationHistory observationHistory;
  private final UtilizationKpiStore store;
  private final UtilizationKpiPolicy policy;
  private final Clock clock;

  public UtilizationKpiService(
      FindEquipmentStateIntervals intervalFinder,
      AccumulatedTimeObservationHistory observationHistory,
      UtilizationKpiStore store,
      UtilizationKpiPolicy policy,
      Clock clock) {
    this.intervalFinder = Objects.requireNonNull(intervalFinder);
    this.observationHistory = Objects.requireNonNull(observationHistory);
    this.store = Objects.requireNonNull(store);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public UtilizationKpiProcessingResult project(UtilizationKpiCommand command) {
    requireSupportedVersion(command.calculationVersion());
    EquipmentStateIntervalProcessingResult intervals =
        intervalFinder.findByProcessingRunId(
            command.machineId(), command.intervalProcessingRunId());
    var intervalReport = intervals.report();
    List<AccumulatedTimeObservation> counters =
        observationHistory.readAccumulatedTimes(
            command.machineId(),
            intervalReport.replaySessionId(),
            intervalReport.throughReplaySequence());
    counters =
        counters.stream()
            .filter(
                observation ->
                    !observation.sourceObservedAt().isBefore(intervalReport.observedFrom())
                        && !observation.sourceObservedAt().isAfter(intervalReport.observedTo()))
            .toList();
    UtilizationKpiReport report =
        policy.calculate(command.intervalProcessingRunId(), intervalReport, counters);
    String processingRunId =
        com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport.sha256(
            command.machineId()
                + "\n"
                + command.intervalProcessingRunId()
                + "\n"
                + command.calculationVersion()
                + "\n"
                + report.inputHash());
    var existing = store.findProcessingRun(processingRunId);
    if (existing.isPresent()) {
      return asResult(existing.get()).asExisting();
    }
    Instant createdAt = clock.instant();
    boolean created = store.preserve(new StoredUtilizationKpi(processingRunId, createdAt, report));
    return created
        ? new UtilizationKpiProcessingResult(processingRunId, createdAt, true, report)
        : asResult(store.findProcessingRun(processingRunId).orElseThrow()).asExisting();
  }

  @Override
  public UtilizationKpiProcessingResult findByProcessingRunId(
      String machineId, String processingRunId) {
    StoredUtilizationKpi stored =
        store
            .findProcessingRun(processingRunId)
            .filter(processing -> processing.report().machineId().equals(machineId))
            .orElseThrow(() -> new UtilizationKpiNotFoundException(processingRunId));
    return asResult(stored).asExisting();
  }

  private static void requireSupportedVersion(String version) {
    if (!UtilizationKpiPolicy.RULE_VERSION.equals(version)) {
      throw new IllegalArgumentException("Unsupported utilization calculation version: " + version);
    }
  }

  private static UtilizationKpiProcessingResult asResult(StoredUtilizationKpi stored) {
    return new UtilizationKpiProcessingResult(
        stored.processingRunId(), stored.createdAt(), false, stored.report());
  }
}
