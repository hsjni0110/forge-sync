package com.forgesync.factoryapi.replay.application;

import java.time.Instant;
import java.util.UUID;

public interface ReplayControlGateway {
  ReplaySessionState prepare(String machineId, String sourceSetId, int speedMultiplier);

  ReplaySessionState current(String machineId);

  ReplaySessionState start(UUID sessionId, long expectedRevision, Instant seekTarget);

  ReplaySessionState pause(UUID sessionId, long expectedRevision);

  ReplaySessionState resume(UUID sessionId, long expectedRevision);

  ReplaySessionState changeSpeed(UUID sessionId, long expectedRevision, int speedMultiplier);

  ReplaySessionState prepareReplacement(UUID sessionId, long expectedRevision, int speedMultiplier);
}
