package com.forgesync.factoryapi.equipmenttwin.application;

import java.util.Objects;
import java.util.UUID;

public record EquipmentStateIntervalCommand(
    String machineId, UUID replaySessionId, long throughReplaySequence, String ruleVersion) {

  public EquipmentStateIntervalCommand {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(replaySessionId, "replaySessionId");
    if (throughReplaySequence < 0) {
      throw new IllegalArgumentException("throughReplaySequence must not be negative");
    }
    Objects.requireNonNull(ruleVersion, "ruleVersion");
  }
}
