package com.forgesync.factoryapi.equipmenttwin.application;

import java.time.Instant;
import java.util.UUID;

public interface ActivateReplayProjection {
  void activate(String machineId, UUID replaySessionId, Instant activatedAt);
}
