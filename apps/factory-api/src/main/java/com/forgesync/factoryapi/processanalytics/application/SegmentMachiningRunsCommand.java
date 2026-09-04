package com.forgesync.factoryapi.processanalytics.application;

import java.util.Objects;
import java.util.UUID;

public record SegmentMachiningRunsCommand(
    String machineId,
    UUID replaySessionId,
    long throughReplaySequence,
    String segmentationRuleVersion) {

  public SegmentMachiningRunsCommand {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (throughReplaySequence < 0) {
      throw new IllegalArgumentException("throughReplaySequence must not be negative");
    }
    Objects.requireNonNull(segmentationRuleVersion, "segmentationRuleVersion");
  }
}
