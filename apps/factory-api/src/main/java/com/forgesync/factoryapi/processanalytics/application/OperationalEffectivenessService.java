package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.AvailabilityComponent;
import com.forgesync.factoryapi.processanalytics.domain.ComponentStatus;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureContext;
import com.forgesync.factoryapi.processanalytics.domain.CyclePerformanceSample;
import com.forgesync.factoryapi.processanalytics.domain.DeterministicHash;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessPolicy;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessReport;
import com.forgesync.factoryapi.processanalytics.domain.ValueProvenance;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OperationalEffectivenessService
    implements ProjectOperationalEffectiveness, FindOperationalEffectiveness {
  private final UtilizationEvidenceSource utilizationSource;
  private final CycleFeatureProcessingSource cycleSource;
  private final MachiningRunProcessingSource machiningSource;
  private final PartCountObservationHistory partCountHistory;
  private final OperationalEffectivenessStore store;
  private final OperationalEffectivenessPolicy policy;
  private final Clock clock;

  public OperationalEffectivenessService(
      UtilizationEvidenceSource utilizationSource,
      CycleFeatureProcessingSource cycleSource,
      MachiningRunProcessingSource machiningSource,
      PartCountObservationHistory partCountHistory,
      OperationalEffectivenessStore store,
      OperationalEffectivenessPolicy policy,
      Clock clock) {
    this.utilizationSource = Objects.requireNonNull(utilizationSource);
    this.cycleSource = Objects.requireNonNull(cycleSource);
    this.machiningSource = Objects.requireNonNull(machiningSource);
    this.partCountHistory = Objects.requireNonNull(partCountHistory);
    this.store = Objects.requireNonNull(store);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public OperationalEffectivenessProcessingResult project(OperationalEffectivenessCommand command) {
    if (!OperationalEffectivenessPolicy.POLICY_VERSION.equals(command.policyVersion())) {
      throw new IllegalArgumentException("Unsupported operational effectiveness policy version");
    }
    var utilization =
        utilizationSource.find(command.machineId(), command.utilizationProcessingRunId());
    var cycles =
        cycleSource
            .findCycleFeatureProcessingRun(command.cycleFeatureProcessingRunId())
            .filter(source -> source.machineId().equals(command.machineId()))
            .orElseThrow(
                () ->
                    new CycleFeatureProcessingNotFoundException(
                        command.cycleFeatureProcessingRunId()));
    var machining =
        machiningSource
            .findProcessingRun(cycles.machiningRunProcessingRunId())
            .filter(source -> source.machineId().equals(command.machineId()))
            .orElseThrow(
                () ->
                    new MachiningRunProcessingNotFoundException(
                        cycles.machiningRunProcessingRunId()));
    if (!utilization.replaySessionId().equals(machining.replaySessionId())
        || utilization.throughReplaySequence() != machining.throughReplaySequence()) {
      throw new IllegalArgumentException(
          "Input processing runs do not describe the same replay cursor");
    }
    String inputHash =
        DeterministicHash.sha256(
            String.join(
                "\n",
                utilization.processingRunId(),
                utilization.resultHash(),
                cycles.featureProcessingRunId(),
                cycles.resultHash(),
                canonicalAssumptions(command)));
    String processingRunId =
        DeterministicHash.sha256(
            String.join("\n", command.machineId(), command.policyVersion(), inputHash));
    var existing = store.find(processingRunId);
    if (existing.isPresent()) return asResult(existing.get()).asExisting();

    Map<String, com.forgesync.factoryapi.processanalytics.domain.MachiningRun> runsById =
        machining.machiningRuns().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    com.forgesync.factoryapi.processanalytics.domain.MachiningRun::machiningRunId,
                    Function.identity()));
    var contexts =
        cycles.featureSets().stream()
            .map(
                set ->
                    new CycleFeatureContext(
                        set.cycleFeatureSetId(),
                        runsById.containsKey(set.cycleFeature().machiningRunId())
                            ? runsById.get(set.cycleFeature().machiningRunId()).programName()
                            : null,
                        set.cycleFeature()))
            .sorted(
                Comparator.comparing((CycleFeatureContext item) -> item.cycleFeature().startedAt())
                    .thenComparing(CycleFeatureContext::cycleFeatureSetId))
            .toList();
    CycleFeatureContext target = contexts.isEmpty() ? null : contexts.getLast();
    var earlier = new ArrayList<CyclePerformanceSample>();
    if (target != null) {
      contexts
          .subList(0, contexts.size() - 1)
          .forEach(
              item ->
                  earlier.add(
                      new CyclePerformanceSample(
                          item.cycleFeatureSetId(),
                          item.programName(),
                          item.cycleFeature().startedAt(),
                          item.cycleFeature().durationSeconds())));
    }
    BigDecimal assumed =
        target == null
            ? null
            : command.assumedIdealCycleSecondsByProgram().get(target.programName());
    var performance =
        policy.performance(
            target == null ? null : target.cycleFeatureSetId(),
            target == null ? null : target.programName(),
            target == null ? null : target.cycleFeature().durationSeconds(),
            earlier,
            assumed);
    var partCounts =
        partCountHistory.readPartCounts(
            command.machineId(),
            utilization.replaySessionId(),
            utilization.throughReplaySequence(),
            utilization.observedFrom(),
            utilization.observedTo());
    var throughput = policy.throughput(partCounts);
    BigDecimal availabilityPercent = utilization.isAvailable() ? utilization.activePercent() : null;
    var availability =
        new AvailabilityComponent(
            availabilityPercent == null ? ComponentStatus.UNAVAILABLE : ComponentStatus.AVAILABLE,
            availabilityPercent,
            ValueProvenance.OBSERVED,
            availabilityPercent == null ? ValueProvenance.UNAVAILABLE : ValueProvenance.DERIVED,
            "ACTIVE_DURATION / OBSERVED_RANGE",
            availabilityPercent == null ? "INSUFFICIENT_STATE_INTERVALS" : null);
    String resultHash =
        DeterministicHash.sha256(
            String.join(
                "\n",
                String.valueOf(availability.percent()),
                String.valueOf(performance.percent()),
                String.valueOf(throughput.partCount()),
                policy.unavailableQuality().reason(),
                policy.unavailableComposite().reason()));
    var report =
        new OperationalEffectivenessReport(
            command.policyVersion(),
            command.machineId(),
            utilization.replaySessionId(),
            utilization.throughReplaySequence(),
            utilization.observedFrom(),
            utilization.observedTo(),
            utilization.processingRunId(),
            cycles.featureProcessingRunId(),
            machining.processingRunId(),
            target == null ? null : target.cycleFeatureSetId(),
            target == null ? null : target.programName(),
            inputHash,
            resultHash,
            availability,
            performance,
            throughput,
            policy.unavailableQuality(),
            policy.unavailableComposite());
    var stored =
        new OperationalEffectivenessStore.StoredOperationalEffectiveness(
            processingRunId, clock.instant(), report);
    boolean created = store.preserve(stored);
    return created
        ? asResult(stored)
        : asResult(store.find(processingRunId).orElseThrow()).asExisting();
  }

  @Override
  public OperationalEffectivenessProcessingResult find(String machineId, String processingRunId) {
    return store
        .find(processingRunId)
        .filter(value -> value.report().machineId().equals(machineId))
        .map(OperationalEffectivenessService::asResult)
        .map(OperationalEffectivenessProcessingResult::asExisting)
        .orElseThrow(() -> new OperationalEffectivenessNotFoundException(processingRunId));
  }

  private static OperationalEffectivenessProcessingResult asResult(
      OperationalEffectivenessStore.StoredOperationalEffectiveness stored) {
    return new OperationalEffectivenessProcessingResult(
        stored.processingRunId(), stored.createdAt(), true, stored.report());
  }

  private static String canonicalAssumptions(OperationalEffectivenessCommand command) {
    return command.assumedIdealCycleSecondsByProgram().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue().stripTrailingZeros().toPlainString())
        .collect(Collectors.joining("\n"));
  }
}
