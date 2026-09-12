package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponseMapper;
import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionNotifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WebSocketTwinProjectionNotifier implements TwinProjectionNotifier {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WebSocketTwinProjectionNotifier.class);

  private final GetOperationalTwinSnapshot getOperationalTwinSnapshot;
  private final TwinSnapshotResponseMapper responseMapper;
  private final TwinPatchBroadcaster broadcaster;
  private final Executor publicationExecutor;
  private final Counter publicationFailures;

  WebSocketTwinProjectionNotifier(
      GetOperationalTwinSnapshot getOperationalTwinSnapshot,
      TwinPatchBroadcaster broadcaster,
      Executor publicationExecutor,
      MeterRegistry meterRegistry) {
    this.getOperationalTwinSnapshot = Objects.requireNonNull(getOperationalTwinSnapshot);
    this.responseMapper = new TwinSnapshotResponseMapper();
    this.broadcaster = Objects.requireNonNull(broadcaster);
    this.publicationExecutor = Objects.requireNonNull(publicationExecutor);
    this.publicationFailures =
        Objects.requireNonNull(meterRegistry).counter("forgesync.twin.patch.publication.failures");
  }

  @Override
  public void projectionCommitted(String machineId) {
    try {
      publicationExecutor.execute(() -> publish(machineId));
    } catch (RuntimeException exception) {
      recordFailure(machineId, exception);
    }
  }

  private void publish(String machineId) {
    try {
      TwinSnapshotResponse snapshot =
          responseMapper.map(getOperationalTwinSnapshot.getSnapshot(machineId));
      long targetVersion = snapshot.consistency().twinVersion();
      broadcaster.broadcast(
          new TwinPatchMessage(
              "1.6.0",
              "TWIN_PATCH",
              machineId,
              targetVersion - 1,
              targetVersion,
              snapshot.consistency().projectedAt(),
              snapshot));
    } catch (RuntimeException exception) {
      recordFailure(machineId, exception);
    }
  }

  private void recordFailure(String machineId, RuntimeException exception) {
    publicationFailures.increment();
    LOGGER.warn("Twin patch publication failed machineId={}", machineId, exception);
  }
}
