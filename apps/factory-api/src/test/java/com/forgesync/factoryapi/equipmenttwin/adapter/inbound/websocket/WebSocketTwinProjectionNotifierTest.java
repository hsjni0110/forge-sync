package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.TwinConsistencyState;
import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebSocketTwinProjectionNotifierTest {

  @Test
  void broadcastsAWholeSnapshotWithContinuousVersions() {
    GetOperationalTwinSnapshot query = mock(GetOperationalTwinSnapshot.class);
    TwinPatchBroadcaster broadcaster = mock(TwinPatchBroadcaster.class);
    when(query.getSnapshot("Mazak01")).thenReturn(snapshot());
    WebSocketTwinProjectionNotifier notifier =
        new WebSocketTwinProjectionNotifier(
            query, broadcaster, Runnable::run, new SimpleMeterRegistry());

    notifier.projectionCommitted("Mazak01");

    ArgumentCaptor<TwinPatchMessage> patch = ArgumentCaptor.forClass(TwinPatchMessage.class);
    verify(broadcaster).broadcast(patch.capture());
    assertThat(patch.getValue().baseVersion()).isEqualTo(3);
    assertThat(patch.getValue().targetVersion()).isEqualTo(4);
    assertThat(patch.getValue().snapshot().machine().machineId()).isEqualTo("Mazak01");
  }

  @Test
  void recordsPublicationFailureWithoutThrowingAfterCommit() {
    GetOperationalTwinSnapshot query = mock(GetOperationalTwinSnapshot.class);
    TwinPatchBroadcaster broadcaster = mock(TwinPatchBroadcaster.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(query.getSnapshot("Mazak01")).thenReturn(snapshot());
    doThrow(new IllegalStateException("socket unavailable")).when(broadcaster).broadcast(any());
    WebSocketTwinProjectionNotifier notifier =
        new WebSocketTwinProjectionNotifier(query, broadcaster, Runnable::run, meterRegistry);

    notifier.projectionCommitted("Mazak01");

    assertThat(meterRegistry.counter("forgesync.twin.patch.publication.failures").count())
        .isEqualTo(1);
  }

  @Test
  void queuesPublicationWithoutRunningSocketIoOnTheIngestionCaller() {
    GetOperationalTwinSnapshot query = mock(GetOperationalTwinSnapshot.class);
    TwinPatchBroadcaster broadcaster = mock(TwinPatchBroadcaster.class);
    AtomicReference<Runnable> queuedPublication = new AtomicReference<>();
    when(query.getSnapshot("Mazak01")).thenReturn(snapshot());
    WebSocketTwinProjectionNotifier notifier =
        new WebSocketTwinProjectionNotifier(
            query, broadcaster, queuedPublication::set, new SimpleMeterRegistry());

    notifier.projectionCommitted("Mazak01");

    verifyNoInteractions(query, broadcaster);
    queuedPublication.get().run();
    verify(broadcaster).broadcast(any());
  }

  private static OperationalTwinSnapshot snapshot() {
    Instant projectedAt = Instant.parse("2026-09-02T01:02:04Z");
    return new OperationalTwinSnapshot(
        "Mazak01",
        new TwinVersion(4),
        projectedAt,
        TwinConsistencyState.PARTIAL,
        List.of(
            "metrics.spindleSpeeds",
            "metrics.bAxisAngle",
            "state.execution",
            "metrics.toolNumber",
            "metrics.program"),
        ConnectivityState.UNKNOWN,
        ExecutionState.UNKNOWN,
        HealthState.UNKNOWN,
        FreshnessState.FRESH,
        projectedAt,
        Duration.ZERO,
        2_000,
        10_000,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of());
  }
}
