package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class MachineTwinHandshakeInterceptorTest {

  @Test
  void acceptsOnlyAStableMachineIdentityFromTheEndpointPath() {
    MachineTwinHandshakeInterceptor interceptor = new MachineTwinHandshakeInterceptor();
    Map<String, Object> attributes = new HashMap<>();
    ServerHttpRequest valid = request("/api/v1/ws/machines/Mazak01/twin");
    ServerHttpRequest invalid = request("/api/v1/ws/machines/invalid%20machine/twin");

    assertThat(
            interceptor.beforeHandshake(
                valid, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes))
        .isTrue();
    assertThat(attributes)
        .containsEntry(MachineTwinHandshakeInterceptor.MACHINE_ID_ATTRIBUTE, "Mazak01");
    assertThat(
            interceptor.beforeHandshake(
                invalid,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                new HashMap<>()))
        .isFalse();
  }

  private static ServerHttpRequest request(String path) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    org.mockito.Mockito.when(request.getURI()).thenReturn(URI.create("http://localhost" + path));
    return request;
  }
}
