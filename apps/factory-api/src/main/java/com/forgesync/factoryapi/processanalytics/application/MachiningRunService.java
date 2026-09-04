package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.DeterministicHash;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunSegmentationPolicy;
import com.forgesync.factoryapi.processanalytics.domain.ProcessFactSourcePolicy;
import com.forgesync.factoryapi.processanalytics.domain.ProcessInputKind;
import com.forgesync.factoryapi.processanalytics.domain.ProcessObservation;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MachiningRunService implements SegmentMachiningRuns, FindMachiningRuns {

  private final CanonicalObservationHistory observationHistory;
  private final MachiningRunProjectionStore projectionStore;
  private final MachiningRunSegmentationPolicy segmentationPolicy;
  private final ProcessFactSourcePolicy sourcePolicy;
  private final Clock clock;

  public MachiningRunService(
      CanonicalObservationHistory observationHistory,
      MachiningRunProjectionStore projectionStore,
      MachiningRunSegmentationPolicy segmentationPolicy,
      ProcessFactSourcePolicy sourcePolicy,
      Clock clock) {
    this.observationHistory = Objects.requireNonNull(observationHistory);
    this.projectionStore = Objects.requireNonNull(projectionStore);
    this.segmentationPolicy = Objects.requireNonNull(segmentationPolicy);
    this.sourcePolicy = Objects.requireNonNull(sourcePolicy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public MachiningRunProcessingResult segment(SegmentMachiningRunsCommand command) {
    sourcePolicy.classify(ProcessInputKind.CANONICAL_OBSERVATION);
    List<ProcessObservation> observations =
        ordered(
            observationHistory.readRelevantObservations(
                command.machineId(), command.replaySessionId(), command.throughReplaySequence()));
    if (observations.isEmpty()) {
      throw new CanonicalObservationHistoryNotFoundException(command.machineId());
    }

    String inputHash = inputHash(observations);
    String processingRunId =
        DeterministicHash.sha256(
            command.machineId()
                + "\n"
                + command.replaySessionId()
                + "\n"
                + command.throughReplaySequence()
                + "\n"
                + command.segmentationRuleVersion()
                + "\n"
                + inputHash);
    var existing = projectionStore.findProcessingRun(processingRunId);
    if (existing.isPresent()) {
      return existing.get().asExisting();
    }

    List<MachiningRun> runs =
        segmentationPolicy.segment(
            processingRunId, command.segmentationRuleVersion(), observations);
    String resultHash =
        DeterministicHash.sha256(
            runs.stream()
                .map(MachiningRun::resultHash)
                .reduce("", (left, right) -> left + "\n" + right));
    MachiningRunProcessingResult result =
        new MachiningRunProcessingResult(
            processingRunId,
            command.machineId(),
            command.replaySessionId(),
            command.throughReplaySequence(),
            command.segmentationRuleVersion(),
            inputHash,
            observations.size(),
            resultHash,
            clock.instant(),
            true,
            runs);
    boolean created = projectionStore.preserve(result);
    return created
        ? result
        : projectionStore.findProcessingRun(processingRunId).orElseThrow().asExisting();
  }

  @Override
  public MachiningRunProcessingResult find(String machineId, String processingRunId) {
    MachiningRunProcessingResult processing =
        projectionStore
            .findProcessingRun(processingRunId)
            .orElseThrow(() -> new MachiningRunProcessingNotFoundException(processingRunId));
    if (!processing.machineId().equals(machineId)) {
      throw new MachiningRunProcessingNotFoundException(processingRunId);
    }
    return processing.asExisting();
  }

  private static List<ProcessObservation> ordered(List<ProcessObservation> observations) {
    return observations.stream()
        .sorted(
            Comparator.comparingLong(ProcessObservation::replaySequence)
                .thenComparing(ProcessObservation::sourceObservedAt)
                .thenComparing(ProcessObservation::sourceEventKey))
        .toList();
  }

  private static String inputHash(List<ProcessObservation> observations) {
    StringBuilder material = new StringBuilder();
    for (ProcessObservation observation : observations) {
      material
          .append(observation.replaySequence())
          .append('|')
          .append(observation.sourceObservedAt())
          .append('|')
          .append(observation.sourceEventKey())
          .append('|')
          .append(observation.signal())
          .append('|')
          .append(observation.isAvailable())
          .append('|')
          .append(Objects.toString(observation.textValue(), ""))
          .append('|')
          .append(Objects.toString(observation.numericValue(), ""))
          .append('|')
          .append(observation.provenance().artifactId())
          .append('|')
          .append(observation.provenance().rawRecordId())
          .append('|')
          .append(observation.provenance().sourceKind())
          .append('|')
          .append(observation.provenance().provider())
          .append('|')
          .append(observation.provenance().sourceSetId())
          .append('|')
          .append(observation.provenance().mappingVersion())
          .append('|')
          .append(observation.provenance().sourceDataItemId())
          .append('\n');
    }
    return DeterministicHash.sha256(material.toString());
  }
}
