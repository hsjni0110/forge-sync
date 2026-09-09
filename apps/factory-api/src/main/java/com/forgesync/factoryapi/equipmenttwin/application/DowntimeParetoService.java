package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class DowntimeParetoService implements ProjectDowntimePareto, FindDowntimePareto {
  private final FindUtilizationKpis utilizationFinder;
  private final FindEquipmentStateIntervals intervalFinder;
  private final ConditionEvidenceHistory conditionHistory;
  private final DowntimeParetoStore store;
  private final DowntimeParetoPolicy policy;
  private final Clock clock;

  public DowntimeParetoService(
      FindUtilizationKpis utilizationFinder,
      FindEquipmentStateIntervals intervalFinder,
      ConditionEvidenceHistory conditionHistory,
      DowntimeParetoStore store,
      DowntimeParetoPolicy policy,
      Clock clock) {
    this.utilizationFinder = Objects.requireNonNull(utilizationFinder);
    this.intervalFinder = Objects.requireNonNull(intervalFinder);
    this.conditionHistory = Objects.requireNonNull(conditionHistory);
    this.store = Objects.requireNonNull(store);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public DowntimeParetoProcessingResult project(DowntimeParetoCommand command) {
    if (!DowntimeParetoPolicy.RULE_VERSION.equals(command.ruleVersion())) {
      throw new IllegalArgumentException("Unsupported downtime Pareto rule version");
    }
    UtilizationKpiProcessingResult utilization =
        utilizationFinder.findByProcessingRunId(
            command.machineId(), command.utilizationProcessingRunId());
    String intervalProcessingRunId = utilization.report().intervalProcessingRunId();
    EquipmentStateIntervalReport intervals =
        intervalFinder.findByProcessingRunId(command.machineId(), intervalProcessingRunId).report();
    var conditions =
        conditionHistory.readAttentionConditions(
            command.machineId(), intervals.replaySessionId(), intervals.throughReplaySequence());
    var report =
        policy.rank(
            command.utilizationProcessingRunId(), intervalProcessingRunId, intervals, conditions);
    String processingRunId =
        EquipmentStateIntervalReport.sha256(
            command.machineId()
                + "\n"
                + command.utilizationProcessingRunId()
                + "\n"
                + command.ruleVersion()
                + "\n"
                + report.inputHash());
    var existing = store.findProcessingRun(processingRunId);
    if (existing.isPresent()) {
      return asResult(existing.get()).asExisting();
    }
    Instant createdAt = clock.instant();
    boolean created =
        store.preserve(
            new DowntimeParetoStore.StoredDowntimePareto(processingRunId, createdAt, report));
    return created
        ? new DowntimeParetoProcessingResult(processingRunId, createdAt, true, report)
        : asResult(store.findProcessingRun(processingRunId).orElseThrow()).asExisting();
  }

  @Override
  public DowntimeParetoProcessingResult findByProcessingRunId(
      String machineId, String processingRunId) {
    var stored =
        store
            .findProcessingRun(processingRunId)
            .filter(processing -> processing.report().machineId().equals(machineId))
            .orElseThrow(() -> new DowntimeParetoNotFoundException(processingRunId));
    return asResult(stored).asExisting();
  }

  private static DowntimeParetoProcessingResult asResult(
      DowntimeParetoStore.StoredDowntimePareto stored) {
    return new DowntimeParetoProcessingResult(
        stored.processingRunId(), stored.createdAt(), true, stored.report());
  }
}
