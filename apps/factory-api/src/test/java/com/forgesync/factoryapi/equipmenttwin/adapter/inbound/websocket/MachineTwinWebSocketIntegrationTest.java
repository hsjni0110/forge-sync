package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "forgesync.ingestion.enabled=false",
      "forgesync.twin.query.enabled=false",
      "forgesync.replay.enabled=false",
      "forgesync.process-analytics.enabled=false",
      "forgesync.equipment-state-intervals.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
@Import(MachineTwinWebSocketIntegrationTest.WebSocketTestConfiguration.class)
class MachineTwinWebSocketIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private TwinPatchBroadcaster broadcaster;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void subscribedBrowserReceivesTheMachineScopedTwinPatch() throws Exception {
    CompletableFuture<String> receivedMessage = new CompletableFuture<>();
    WebSocket socket =
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://127.0.0.1:" + port + "/api/v1/ws/machines/Mazak01/twin"),
                new TextListener(receivedMessage))
            .get(5, TimeUnit.SECONDS);

    broadcaster.broadcast(patch());

    assertThat(receivedMessage.get(5, TimeUnit.SECONDS)).contains("\"targetVersion\":4");
    socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").get(5, TimeUnit.SECONDS);
  }

  private TwinPatchMessage patch() throws Exception {
    TwinSnapshotResponse snapshot;
    try (InputStream stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("fixtures/twin/v1/mazak01-operational-twin.json")) {
      snapshot = objectMapper.readValue(stream, TwinSnapshotResponse.class);
    }
    return new TwinPatchMessage(
        "1.6.0", "TWIN_PATCH", "Mazak01", 3, 4, Instant.parse("2026-09-02T01:02:04Z"), snapshot);
  }

  private record TextListener(CompletableFuture<String> message) implements WebSocket.Listener {
    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      message.complete(data.toString());
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }
  }

  @TestConfiguration
  @EnableWebSocket
  static class WebSocketTestConfiguration {

    @Bean
    TwinPatchBroadcaster twinPatchBroadcaster(ObjectMapper objectMapper) {
      return new TwinPatchBroadcaster(objectMapper);
    }

    @Bean
    WebSocketConfigurer testWebSocketConfigurer(TwinPatchBroadcaster broadcaster) {
      MachineTwinWebSocketHandler handler = new MachineTwinWebSocketHandler(broadcaster);
      return registry ->
          registry
              .addHandler(handler, "/api/v1/ws/machines/*/twin")
              .addInterceptors(new MachineTwinHandshakeInterceptor())
              .setAllowedOrigins("http://localhost:5173");
    }
  }
}
