package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.GetOperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.TwinProjectionNotifier;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(
    name = "forgesync.twin.query.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TwinWebSocketConfiguration implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  TwinWebSocketConfiguration(
      @Value("${forgesync.websocket.allowed-origins:http://localhost:5173}")
          List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins.toArray(String[]::new);
  }

  @Bean
  TwinPatchBroadcaster twinPatchBroadcaster(ObjectMapper objectMapper) {
    return new TwinPatchBroadcaster(objectMapper);
  }

  @Bean
  MachineTwinWebSocketHandler machineTwinWebSocketHandler(TwinPatchBroadcaster broadcaster) {
    return new MachineTwinWebSocketHandler(broadcaster);
  }

  @Bean
  TwinProjectionNotifier twinProjectionNotifier(
      GetOperationalTwinSnapshot getOperationalTwinSnapshot,
      TwinPatchBroadcaster broadcaster,
      MeterRegistry meterRegistry) {
    return new WebSocketTwinProjectionNotifier(
        getOperationalTwinSnapshot, broadcaster, meterRegistry);
  }

  @Bean
  WebSocketConfigurer twinWebSocketConfigurer(MachineTwinWebSocketHandler webSocketHandler) {
    return registry -> registerWebSocketHandler(registry, webSocketHandler);
  }

  private void registerWebSocketHandler(
      WebSocketHandlerRegistry registry, MachineTwinWebSocketHandler webSocketHandler) {
    registry
        .addHandler(webSocketHandler, "/api/v1/ws/machines/*/twin")
        .addInterceptors(new MachineTwinHandshakeInterceptor())
        .setAllowedOrigins(allowedOrigins);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/v1/machines/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET")
        .allowedHeaders("Accept");
  }
}
