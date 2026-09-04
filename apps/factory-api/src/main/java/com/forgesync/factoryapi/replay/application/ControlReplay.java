package com.forgesync.factoryapi.replay.application;

import com.forgesync.factoryapi.equipmenttwin.application.ActivateReplayProjection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ControlReplay {
  private final ReplayControlGateway gateway;
  private final ActivateReplayProjection projectionActivator;
  private final Clock clock;

  public ControlReplay(
      ReplayControlGateway gateway, ActivateReplayProjection projectionActivator, Clock clock) {
    this.gateway = Objects.requireNonNull(gateway);
    this.projectionActivator = Objects.requireNonNull(projectionActivator);
    this.clock = Objects.requireNonNull(clock);
  }

  public synchronized ReplaySessionState start(
      String machineId, String sourceSetId, int speedMultiplier) {
    requireSpeed(speedMultiplier);
    ReplaySessionState prepared = gateway.prepare(machineId, sourceSetId, speedMultiplier);
    projectionActivator.activate(machineId, prepared.replaySessionId(), clock.instant());
    return gateway.start(prepared.replaySessionId(), prepared.revision(), null);
  }

  public synchronized ReplaySessionState seek(
      UUID sessionId, long expectedRevision, Instant target, int speedMultiplier) {
    Objects.requireNonNull(target, "target");
    requireSpeed(speedMultiplier);
    ReplaySessionState prepared =
        gateway.prepareReplacement(sessionId, expectedRevision, speedMultiplier);
    projectionActivator.activate(prepared.machineId(), prepared.replaySessionId(), clock.instant());
    return gateway.start(prepared.replaySessionId(), prepared.revision(), target);
  }

  public ReplaySessionState current(String machineId) {
    return gateway.current(machineId);
  }

  public ReplaySessionState pause(UUID sessionId, long expectedRevision) {
    return gateway.pause(sessionId, expectedRevision);
  }

  public ReplaySessionState resume(UUID sessionId, long expectedRevision) {
    return gateway.resume(sessionId, expectedRevision);
  }

  public ReplaySessionState changeSpeed(
      UUID sessionId, long expectedRevision, int speedMultiplier) {
    requireSpeed(speedMultiplier);
    return gateway.changeSpeed(sessionId, expectedRevision, speedMultiplier);
  }

  private static void requireSpeed(int speedMultiplier) {
    if (speedMultiplier != 1 && speedMultiplier != 10 && speedMultiplier != 100) {
      throw new IllegalArgumentException("Replay speed must be 1, 10, or 100");
    }
  }
}
