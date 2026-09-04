package com.forgesync.factoryapi.processanalytics.application;

import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MachiningRunProcessingResult(
    String processingRunId,
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    String segmentationRuleVersion,
    String inputHash,
    int inputObservationCount,
    String resultHash,
    Instant createdAt,
    boolean isCreated,
    List<MachiningRun> machiningRuns) {

  public MachiningRunProcessingResult {
    Objects.requireNonNull(processingRunId, "processingRunId");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    Objects.requireNonNull(segmentationRuleVersion, "segmentationRuleVersion");
    Objects.requireNonNull(inputHash, "inputHash");
    Objects.requireNonNull(resultHash, "resultHash");
    Objects.requireNonNull(createdAt, "createdAt");
    machiningRuns = List.copyOf(machiningRuns);
  }

  public MachiningRunProcessingResult asExisting() {
    return new MachiningRunProcessingResult(
        processingRunId,
        machineId,
        replaySessionId,
        throughReplaySequence,
        segmentationRuleVersion,
        inputHash,
        inputObservationCount,
        resultHash,
        createdAt,
        false,
        machiningRuns);
  }
}
