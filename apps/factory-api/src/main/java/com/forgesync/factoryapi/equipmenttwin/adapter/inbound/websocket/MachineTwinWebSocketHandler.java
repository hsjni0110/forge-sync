package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import java.util.Objects;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

final class MachineTwinWebSocketHandler extends TextWebSocketHandler {

  private final TwinPatchBroadcaster broadcaster;

  MachineTwinWebSocketHandler(TwinPatchBroadcaster broadcaster) {
    this.broadcaster = Objects.requireNonNull(broadcaster);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    broadcaster.subscribe(machineId(session), session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Twin socket is server-to-client only"));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    broadcaster.unsubscribe(machineId(session), session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    broadcaster.unsubscribe(machineId(session), session.getId());
  }

  private static String machineId(WebSocketSession session) {
    return Objects.toString(
        session.getAttributes().get(MachineTwinHandshakeInterceptor.MACHINE_ID_ATTRIBUTE));
  }
}
