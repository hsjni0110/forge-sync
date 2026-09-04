package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import com.forgesync.factoryapi.processanalytics.domain.CycleObservation;
import com.forgesync.factoryapi.processanalytics.domain.DeterministicHash;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CycleFeatureService implements ProcessCycleFeatures, FindCycleFeatures {
  private final MachiningRunProcessingSource machiningRunSource;
  private final CycleFeatureObservationHistory observationHistory;
  private final CycleFeatureProjectionStore projectionStore;
  private final CycleFeatureExtractor extractor;
  private final Clock clock;

  public CycleFeatureService(
      MachiningRunProcessingSource machiningRunSource,
      CycleFeatureObservationHistory observationHistory,
      CycleFeatureProjectionStore projectionStore,
      CycleFeatureExtractor extractor,
      Clock clock) {
    this.machiningRunSource = Objects.requireNonNull(machiningRunSource);
    this.observationHistory = Objects.requireNonNull(observationHistory);
    this.projectionStore = Objects.requireNonNull(projectionStore);
    this.extractor = Objects.requireNonNull(extractor);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public CycleFeatureProcessingResult process(ProcessCycleFeaturesCommand command) {
    MachiningRunProcessingResult source =
        machiningRunSource
            .findProcessingRun(command.machiningRunProcessingRunId())
            .filter(result -> result.machineId().equals(command.machineId()))
            .orElseThrow(
                () ->
                    new MachiningRunProcessingNotFoundException(
                        command.machiningRunProcessingRunId()));
    List<MachiningRun> eligibleRuns =
        source.machiningRuns().stream()
            .filter(run -> run.status() == MachiningRunStatus.COMPLETED)
            .sorted(
                Comparator.comparing(MachiningRun::startedAt)
                    .thenComparing(MachiningRun::machiningRunId))
            .toList();
    List<CycleObservation> observations = readObservations(source, eligibleRuns);
    String inputHash = inputHash(eligibleRuns, observations);
    String featureProcessingRunId =
        DeterministicHash.sha256(
            lengthPrefixed(
                command.machineId(),
                command.machiningRunProcessingRunId(),
                command.cycleFeatureVersion(),
                inputHash));
    var existing = projectionStore.findCycleFeatureProcessingRun(featureProcessingRunId);
    if (existing.isPresent()) return existing.get().asExisting();

    List<CycleFeatureSet> featureSets =
        eligibleRuns.stream()
            .map(
                run ->
                    new CycleFeatureSet(
                        DeterministicHash.sha256(
                            lengthPrefixed(
                                featureProcessingRunId,
                                run.machiningRunId(),
                                command.cycleFeatureVersion())),
                        extractor.extract(run, observations)))
            .toList();
    String resultHash =
        DeterministicHash.sha256(
            lengthPrefixed(
                (Object[])
                    featureSets.stream()
                        .map(set -> set.cycleFeature().resultHash())
                        .toArray(String[]::new)));
    CycleFeatureProcessingResult result =
        new CycleFeatureProcessingResult(
            featureProcessingRunId,
            command.machiningRunProcessingRunId(),
            command.machineId(),
            command.cycleFeatureVersion(),
            inputHash,
            observations.size(),
            eligibleRuns.size(),
            resultHash,
            clock.instant(),
            true,
            featureSets);
    boolean created = projectionStore.preserve(result);
    return created
        ? result
        : projectionStore
            .findCycleFeatureProcessingRun(featureProcessingRunId)
            .orElseThrow()
            .asExisting();
  }

  @Override
  public CycleFeatureProcessingResult find(String machineId, String featureProcessingRunId) {
    return projectionStore
        .findCycleFeatureProcessingRun(featureProcessingRunId)
        .filter(result -> result.machineId().equals(machineId))
        .map(CycleFeatureProcessingResult::asExisting)
        .orElseThrow(() -> new CycleFeatureProcessingNotFoundException(featureProcessingRunId));
  }

  private List<CycleObservation> readObservations(
      MachiningRunProcessingResult source, List<MachiningRun> eligibleRuns) {
    if (eligibleRuns.isEmpty()) return List.of();
    Instant beforeExclusive =
        eligibleRuns.stream().map(MachiningRun::endedAt).max(Instant::compareTo).orElseThrow();
    List<CycleObservation> candidates =
        observationHistory.readCycleObservations(
            source.machineId(),
            source.replaySessionId(),
            source.throughReplaySequence(),
            beforeExclusive);
    return eligibleRuns.stream()
        .flatMap(run -> extractor.relevantObservations(run, candidates).stream())
        .distinct()
        .sorted(
            Comparator.comparingLong(CycleObservation::replaySequence)
                .thenComparing(CycleObservation::sourceObservedAt)
                .thenComparing(CycleObservation::sourceEventKey))
        .toList();
  }

  private static String inputHash(
      List<MachiningRun> eligibleRuns, List<CycleObservation> observations) {
    StringBuilder material = new StringBuilder("cycle-feature-input:2;");
    eligibleRuns.forEach(run -> append(material, run.machiningRunId(), run.resultHash()));
    observations.forEach(
        observation ->
            append(
                material,
                observation.replaySequence(),
                observation.sourceObservedAt(),
                observation.sourceEventKey(),
                observation.signal(),
                observation.componentId(),
                observation.sourceDataItemId(),
                observation.unit(),
                observation.isAvailable(),
                observation.textValue(),
                observation.numericValue(),
                observation.provenance()));
    return DeterministicHash.sha256(material.toString());
  }

  private static String lengthPrefixed(Object... fields) {
    StringBuilder material = new StringBuilder();
    append(material, fields);
    return material.toString();
  }

  private static void append(StringBuilder material, Object... fields) {
    for (Object field : fields) {
      if (field == null) {
        material.append("null;");
      } else {
        String value = field.toString();
        material
            .append(value.getBytes(StandardCharsets.UTF_8).length)
            .append(':')
            .append(value)
            .append(';');
      }
    }
  }
}
