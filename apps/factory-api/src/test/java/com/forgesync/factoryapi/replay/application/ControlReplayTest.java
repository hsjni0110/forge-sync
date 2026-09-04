package com.forgesync.factoryapi.replay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.equipmenttwin.application.ActivateReplayProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControlReplayTest {
  private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
  private static final UUID SESSION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

  @Test
  void activatesPreparedProjectionBeforeStartingPublication() {
    CallOrder callOrder = new CallOrder();
    RecordingGateway gateway = new RecordingGateway(callOrder);
    RecordingActivator activator = new RecordingActivator(callOrder);
    ControlReplay control = new ControlReplay(gateway, activator, Clock.fixed(NOW, ZoneOffset.UTC));

    ReplaySessionState result = control.start("Mazak01", "nist-source", 10);

    assertThat(result.status()).isEqualTo("RUNNING");
    assertThat(callOrder.events).containsExactly("prepare", "activate", "start");
    assertThat(activator.sessionId).isEqualTo(SESSION_ID);
  }

  @Test
  void rejectsUnsupportedSpeedBeforeCallingEdge() {
    RecordingGateway gateway = new RecordingGateway(new CallOrder());
    ControlReplay control =
        new ControlReplay(
            gateway, new RecordingActivator(new CallOrder()), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> control.start("Mazak01", "nist-source", 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1, 10, or 100");
    assertThat(gateway.prepareCalls).isZero();
  }

  @Test
  void reconcilesAStartWhoseSuccessfulResponseWasLost() {
    CallOrder callOrder = new CallOrder();
    RecordingGateway gateway = new RecordingGateway(callOrder);
    gateway.loseFirstStartResponse = true;
    ControlReplay control =
        new ControlReplay(
            gateway, new RecordingActivator(callOrder), Clock.fixed(NOW, ZoneOffset.UTC));

    ReplaySessionState result = control.start("Mazak01", "nist-source", 10);

    assertThat(result.status()).isEqualTo("RUNNING");
    assertThat(callOrder.events).containsExactly("prepare", "activate", "start", "current");
    assertThat(gateway.startCalls).isEqualTo(1);
  }

  @Test
  void retriesTheSamePreparedSessionWhenStartDidNotReachEdge() {
    CallOrder callOrder = new CallOrder();
    RecordingGateway gateway = new RecordingGateway(callOrder);
    gateway.rejectFirstStartBeforeTransition = true;
    ControlReplay control =
        new ControlReplay(
            gateway, new RecordingActivator(callOrder), Clock.fixed(NOW, ZoneOffset.UTC));

    ReplaySessionState result = control.start("Mazak01", "nist-source", 10);

    assertThat(result.status()).isEqualTo("RUNNING");
    assertThat(callOrder.events)
        .containsExactly("prepare", "activate", "start", "current", "start");
    assertThat(gateway.startCalls).isEqualTo(2);
  }

  @Test
  void passesTheSeekTargetWhilePreparingAReplacement() {
    CallOrder callOrder = new CallOrder();
    RecordingGateway gateway = new RecordingGateway(callOrder);
    ControlReplay control =
        new ControlReplay(
            gateway, new RecordingActivator(callOrder), Clock.fixed(NOW, ZoneOffset.UTC));
    Instant target = NOW.plusSeconds(5);

    control.seek(SESSION_ID, 1, target, 100);

    assertThat(gateway.replacementTarget).isEqualTo(target);
  }

  private static ReplaySessionState state(String status, long revision) {
    return new ReplaySessionState(
        "1.0.0",
        SESSION_ID,
        "Mazak01",
        "nist-source",
        status,
        10,
        revision,
        new ReplaySessionState.SourceRange(NOW, NOW.plusSeconds(10)),
        null,
        null);
  }

  private static final class RecordingActivator implements ActivateReplayProjection {
    private final CallOrder callOrder;
    private UUID sessionId;

    private RecordingActivator(CallOrder callOrder) {
      this.callOrder = callOrder;
    }

    @Override
    public void activate(String machineId, UUID replaySessionId, Instant activatedAt) {
      assertThat(machineId).isEqualTo("Mazak01");
      assertThat(activatedAt).isEqualTo(NOW);
      callOrder.events.add("activate");
      sessionId = replaySessionId;
    }
  }

  private static final class RecordingGateway implements ReplayControlGateway {
    private final CallOrder callOrder;
    private int prepareCalls;
    private int startCalls;
    private boolean loseFirstStartResponse;
    private boolean rejectFirstStartBeforeTransition;
    private Instant replacementTarget;
    private ReplaySessionState currentState = state("RUNNING", 1);

    private RecordingGateway(CallOrder callOrder) {
      this.callOrder = callOrder;
    }

    @Override
    public ReplaySessionState prepare(String machineId, String sourceSetId, int speedMultiplier) {
      prepareCalls++;
      callOrder.events.add("prepare");
      currentState = state("PREPARING", 0);
      return currentState;
    }

    @Override
    public ReplaySessionState start(UUID sessionId, long expectedRevision, Instant seekTarget) {
      startCalls++;
      callOrder.events.add("start");
      if (rejectFirstStartBeforeTransition) {
        rejectFirstStartBeforeTransition = false;
        throw new IllegalStateException("request did not reach Edge");
      }
      currentState = state("RUNNING", 1);
      if (loseFirstStartResponse) {
        loseFirstStartResponse = false;
        throw new IllegalStateException("response lost");
      }
      return currentState;
    }

    @Override
    public ReplaySessionState current(String machineId) {
      callOrder.events.add("current");
      return currentState;
    }

    @Override
    public ReplaySessionState pause(UUID id, long revision) {
      return state("PAUSED", 2);
    }

    @Override
    public ReplaySessionState resume(UUID id, long revision) {
      return state("RUNNING", 3);
    }

    @Override
    public ReplaySessionState changeSpeed(UUID id, long revision, int speed) {
      return state("RUNNING", 4);
    }

    @Override
    public ReplaySessionState prepareReplacement(
        UUID id, long revision, int speed, Instant seekTarget) {
      replacementTarget = seekTarget;
      currentState = state("PREPARING", 0);
      return currentState;
    }
  }

  private static final class CallOrder {
    private final java.util.List<String> events = new java.util.ArrayList<>();
  }
}
