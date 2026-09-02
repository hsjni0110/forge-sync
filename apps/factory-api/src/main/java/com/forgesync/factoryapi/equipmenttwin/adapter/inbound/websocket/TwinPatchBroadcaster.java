package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

final class TwinPatchBroadcaster {

  private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
  private static final int BUFFER_SIZE_BYTES = 1_048_576;

  private final ObjectMapper objectMapper;
  private final Map<String, Map<String, WebSocketSession>> sessionsByMachine =
      new ConcurrentHashMap<>();

  TwinPatchBroadcaster(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  void subscribe(String machineId, WebSocketSession session) {
    WebSocketSession concurrentSession =
        new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_BYTES);
    sessionsByMachine
        .computeIfAbsent(machineId, ignored -> new ConcurrentHashMap<>())
        .put(session.getId(), concurrentSession);
  }

  void unsubscribe(String machineId, String sessionId) {
    Map<String, WebSocketSession> sessions = sessionsByMachine.get(machineId);
    if (sessions == null) {
      return;
    }
    sessions.remove(sessionId);
    if (sessions.isEmpty()) {
      sessionsByMachine.remove(machineId, sessions);
    }
  }

  void broadcast(TwinPatchMessage patch) {
    TextMessage message = new TextMessage(write(patch));
    Map<String, WebSocketSession> sessions = sessionsByMachine.get(patch.machineId());
    if (sessions == null) {
      return;
    }
    TwinPatchPublicationException failure = null;
    for (Map.Entry<String, WebSocketSession> subscription : sessions.entrySet()) {
      try {
        send(subscription.getValue(), message);
      } catch (RuntimeException exception) {
        sessions.remove(subscription.getKey());
        failure =
            new TwinPatchPublicationException(
                "Twin patch delivery failed for at least one subscriber", exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private String write(TwinPatchMessage patch) {
    try {
      return objectMapper.writeValueAsString(patch);
    } catch (JsonProcessingException exception) {
      throw new TwinPatchPublicationException("Twin patch serialization failed", exception);
    }
  }

  private static void send(WebSocketSession session, TextMessage message) {
    if (!session.isOpen()) {
      return;
    }
    try {
      session.sendMessage(message);
    } catch (IOException exception) {
      throw new TwinPatchPublicationException("Twin patch delivery failed", exception);
    }
  }
}
