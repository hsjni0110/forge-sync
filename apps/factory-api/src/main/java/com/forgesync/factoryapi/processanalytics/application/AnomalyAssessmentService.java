package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessmentPolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleBaselinePolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureContext;
import com.forgesync.factoryapi.processanalytics.domain.DeterministicHash;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AnomalyAssessmentService
    implements ProcessAnomalyAssessments, FindAnomalyAssessments {
  private final CycleFeatureProcessingSource cycleFeatureSource;
  private final MachiningRunProcessingSource machiningRunSource;
  private final AnomalyAssessmentProjectionStore projectionStore;
  private final CycleBaselinePolicy baselinePolicy;
  private final AnomalyAssessmentPolicy assessmentPolicy;
  private final Clock clock;

  public AnomalyAssessmentService(
      CycleFeatureProcessingSource cycleFeatureSource,
      MachiningRunProcessingSource machiningRunSource,
      AnomalyAssessmentProjectionStore projectionStore,
      CycleBaselinePolicy baselinePolicy,
      AnomalyAssessmentPolicy assessmentPolicy,
      Clock clock) {
    this.cycleFeatureSource = Objects.requireNonNull(cycleFeatureSource);
    this.machiningRunSource = Objects.requireNonNull(machiningRunSource);
    this.projectionStore = Objects.requireNonNull(projectionStore);
    this.baselinePolicy = Objects.requireNonNull(baselinePolicy);
    this.assessmentPolicy = Objects.requireNonNull(assessmentPolicy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public AnomalyAssessmentProcessingResult process(ProcessAnomalyAssessmentsCommand command) {
    if (!CycleBaselinePolicy.POLICY_VERSION.equals(command.baselinePolicyVersion())) {
      throw new IllegalArgumentException("Unsupported baselinePolicyVersion");
    }
    if (!AnomalyAssessmentPolicy.POLICY_VERSION.equals(command.anomalyAssessmentVersion())) {
      throw new IllegalArgumentException("Unsupported anomalyAssessmentVersion");
    }
    CycleFeatureProcessingResult cycleSource =
        cycleFeatureSource
            .findCycleFeatureProcessingRun(command.cycleFeatureProcessingRunId())
            .filter(result -> result.machineId().equals(command.machineId()))
            .orElseThrow(
                () ->
                    new CycleFeatureProcessingNotFoundException(
                        command.cycleFeatureProcessingRunId()));
    MachiningRunProcessingResult machiningSource =
        machiningRunSource
            .findProcessingRun(cycleSource.machiningRunProcessingRunId())
            .filter(result -> result.machineId().equals(command.machineId()))
            .orElseThrow(
                () ->
                    new MachiningRunProcessingNotFoundException(
                        cycleSource.machiningRunProcessingRunId()));
    String inputHash =
        DeterministicHash.sha256(
            CycleBaselinePolicy.lengthPrefixed(
                cycleSource.featureProcessingRunId(),
                cycleSource.inputHash(),
                cycleSource.resultHash(),
                machiningSource.processingRunId(),
                machiningSource.resultHash()));
    String processingRunId =
        DeterministicHash.sha256(
            CycleBaselinePolicy.lengthPrefixed(
                command.machineId(),
                command.cycleFeatureProcessingRunId(),
                command.baselinePolicyVersion(),
                command.anomalyAssessmentVersion(),
                inputHash));
    var existing = projectionStore.findAnomalyAssessmentProcessingRun(processingRunId);
    if (existing.isPresent()) return existing.get().asExisting();

    Map<String, com.forgesync.factoryapi.processanalytics.domain.MachiningRun> runsById =
        machiningSource.machiningRuns().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    com.forgesync.factoryapi.processanalytics.domain.MachiningRun::machiningRunId,
                    Function.identity()));
    var contexts =
        cycleSource.featureSets().stream()
            .map(
                set ->
                    new CycleFeatureContext(
                        set.cycleFeatureSetId(),
                        runsById.containsKey(set.cycleFeature().machiningRunId())
                            ? runsById.get(set.cycleFeature().machiningRunId()).programName()
                            : null,
                        set.cycleFeature()))
            .sorted(
                Comparator.comparing(
                        (CycleFeatureContext value) -> value.cycleFeature().startedAt())
                    .thenComparing(CycleFeatureContext::cycleFeatureSetId))
            .toList();
    var assessments =
        new ArrayList<com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessment>();
    var evaluated = new ArrayList<CycleFeatureContext>();
    for (CycleFeatureContext target : contexts) {
      var baseline = baselinePolicy.build(command.machineId(), target, evaluated);
      String assessmentId =
          DeterministicHash.sha256(
              CycleBaselinePolicy.lengthPrefixed(
                  processingRunId, target.cycleFeatureSetId(), baseline.baselineGroupId()));
      assessments.add(assessmentPolicy.assess(assessmentId, target, baseline));
      evaluated.add(target);
    }
    String resultHash =
        DeterministicHash.sha256(
            CycleBaselinePolicy.lengthPrefixed(
                assessments.stream().map(value -> value.resultHash()).toArray()));
    var result =
        new AnomalyAssessmentProcessingResult(
            processingRunId,
            cycleSource.featureProcessingRunId(),
            machiningSource.processingRunId(),
            command.machineId(),
            cycleSource.cycleFeatureVersion(),
            command.baselinePolicyVersion(),
            command.anomalyAssessmentVersion(),
            inputHash,
            resultHash,
            clock.instant(),
            true,
            assessments);
    boolean created = projectionStore.preserve(result);
    return created
        ? result
        : projectionStore
            .findAnomalyAssessmentProcessingRun(processingRunId)
            .orElseThrow()
            .asExisting();
  }

  @Override
  public AnomalyAssessmentProcessingResult find(
      String machineId, String assessmentProcessingRunId) {
    return projectionStore
        .findAnomalyAssessmentProcessingRun(assessmentProcessingRunId)
        .filter(result -> result.machineId().equals(machineId))
        .map(AnomalyAssessmentProcessingResult::asExisting)
        .orElseThrow(
            () -> new AnomalyAssessmentProcessingNotFoundException(assessmentProcessingRunId));
  }
}
