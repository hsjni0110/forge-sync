package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse;
import java.time.Instant;

public record TwinPatchMessage(
    String schemaVersion,
    String type,
    String machineId,
    long baseVersion,
    long targetVersion,
    Instant projectedAt,
    TwinSnapshotResponse snapshot) {

  public TwinPatchMessage {
    if (!"1.0.0".equals(schemaVersion) || !"TWIN_PATCH".equals(type)) {
      throw new IllegalArgumentException("Unsupported Twin patch contract");
    }
    if (!machineId.equals(snapshot.machine().machineId())
        || targetVersion != snapshot.consistency().twinVersion()
        || targetVersion != baseVersion + 1
        || !projectedAt.equals(snapshot.consistency().projectedAt())) {
      throw new IllegalArgumentException("Twin patch identity or version is inconsistent");
    }
  }
}
