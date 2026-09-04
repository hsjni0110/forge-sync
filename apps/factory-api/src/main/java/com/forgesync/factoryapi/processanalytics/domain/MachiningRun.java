package com.forgesync.factoryapi.processanalytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MachiningRun(
    String machiningRunId,
    String processingRunId,
    String segmentationRuleVersion,
    String machineId,
    MachiningRunStatus status,
    String programName,
    Instant startedAt,
    Instant endedAt,
    SegmentationConfidence confidence,
    List<String> confidenceReasons,
    ObservationRange observationRange,
    BoundaryEvidence startEvidence,
    BoundaryEvidence endEvidence,
    List<BoundaryEvidence> supportingEvidence,
    String resultHash) {

  public MachiningRun {
    Objects.requireNonNull(machiningRunId, "machiningRunId");
    Objects.requireNonNull(processingRunId, "processingRunId");
    Objects.requireNonNull(segmentationRuleVersion, "segmentationRuleVersion");
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(confidence, "confidence");
    confidenceReasons = List.copyOf(confidenceReasons);
    Objects.requireNonNull(observationRange, "observationRange");
    Objects.requireNonNull(startEvidence, "startEvidence");
    supportingEvidence = List.copyOf(supportingEvidence);
    Objects.requireNonNull(resultHash, "resultHash");
    if (endedAt != null && endedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("endedAt must not precede startedAt");
    }
    if (status == MachiningRunStatus.RUNNING || status == MachiningRunStatus.PENDING) {
      throw new IllegalArgumentException("A projected MachiningRun must be terminal");
    }
  }
}
