package com.forgesync.factoryapi.adapter.inbound.mqtt;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MqttObservationPacket(
    String topic,
    byte[] payload,
    int qos,
    boolean retained,
    String contentType,
    Integer payloadFormatIndicator,
    Map<String, List<String>> userProperties) {

  public MqttObservationPacket {
    payload = payload.clone();
    userProperties =
        userProperties.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }
}
