package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class TwinPatchBroadcasterTest {

  @Test
  void sendsOnlyToSubscribersOfThePatchedMachine() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    TwinPatchBroadcaster broadcaster = new TwinPatchBroadcaster(objectMapper);
    WebSocketSession mazakSession = openSession("mazak-session");
    WebSocketSession otherSession = openSession("other-session");
    broadcaster.subscribe("Mazak01", mazakSession);
    broadcaster.subscribe("OtherMachine", otherSession);
    TwinSnapshotResponse snapshot;
    try (InputStream stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("fixtures/twin/v1/mazak01-operational-twin.json")) {
      snapshot = objectMapper.readValue(stream, TwinSnapshotResponse.class);
    }

    broadcaster.broadcast(
        new TwinPatchMessage(
            "1.4.0",
            "TWIN_PATCH",
            "Mazak01",
            3,
            4,
            Instant.parse("2026-09-02T01:02:04Z"),
            snapshot));

    verify(mazakSession).sendMessage(any(TextMessage.class));
    verify(otherSession, never()).sendMessage(any(TextMessage.class));
  }

  @Test
  void oneBrokenSubscriberDoesNotPreventDeliveryToAnotherSubscriber() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    TwinPatchBroadcaster broadcaster = new TwinPatchBroadcaster(objectMapper);
    WebSocketSession brokenSession = openSession("broken-session");
    WebSocketSession healthySession = openSession("healthy-session");
    doThrow(new IOException("connection closed"))
        .when(brokenSession)
        .sendMessage(any(TextMessage.class));
    broadcaster.subscribe("Mazak01", brokenSession);
    broadcaster.subscribe("Mazak01", healthySession);
    TwinSnapshotResponse snapshot;
    try (InputStream stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("fixtures/twin/v1/mazak01-operational-twin.json")) {
      snapshot = objectMapper.readValue(stream, TwinSnapshotResponse.class);
    }
    TwinPatchMessage patch =
        new TwinPatchMessage(
            "1.4.0",
            "TWIN_PATCH",
            "Mazak01",
            3,
            4,
            Instant.parse("2026-09-02T01:02:04Z"),
            snapshot);

    assertThatThrownBy(() -> broadcaster.broadcast(patch))
        .isInstanceOf(TwinPatchPublicationException.class);
    verify(healthySession).sendMessage(any(TextMessage.class));
  }

  private static WebSocketSession openSession(String sessionId) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(sessionId);
    when(session.isOpen()).thenReturn(true);
    return session;
  }
}
