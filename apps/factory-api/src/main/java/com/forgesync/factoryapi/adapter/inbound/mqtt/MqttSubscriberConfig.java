package com.forgesync.factoryapi.adapter.inbound.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgesync.mqtt")
public record MqttSubscriberConfig(
    String serverUri, String clientId, String username, String password) {

  public MqttSubscriberConfig {
    if (serverUri == null || serverUri.isBlank() || clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("MQTT server URI and client ID must not be blank");
    }
    if (username == null && password != null) {
      throw new IllegalArgumentException("MQTT password requires username");
    }
  }
}
