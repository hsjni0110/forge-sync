package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

final class MachineTwinHandshakeInterceptor implements HandshakeInterceptor {

  static final String MACHINE_ID_ATTRIBUTE = "forgesync.machineId";
  private static final Pattern PATH =
      Pattern.compile("^/api/v1/ws/machines/([A-Za-z0-9._-]{1,64})/twin$");

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    Matcher matcher = PATH.matcher(request.getURI().getPath());
    if (!matcher.matches()) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }
    attributes.put(MACHINE_ID_ATTRIBUTE, matcher.group(1));
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
