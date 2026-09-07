package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads one Canonical Observation as a state-signal observation, without reinterpreting it. */
final class CanonicalStateSignalReader {

  private static final Map<String, StateSignal> SIGNALS =
      Map.of(
          "EXECUTION", StateSignal.EXECUTION,
          "CONTROLLER_MODE", StateSignal.CONTROLLER_MODE,
          "POWER_STATE", StateSignal.POWER_STATE,
          "EMERGENCY_STOP", StateSignal.EMERGENCY_STOP);

  private final ObjectMapper objectMapper;

  CanonicalStateSignalReader(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  Optional<ReadSignal> read(String canonicalEnvelope) {
    try {
      JsonNode payload = objectMapper.readTree(canonicalEnvelope).path("payload");
      StateSignal signal = SIGNALS.get(payload.path("eventType").asText(""));
      if (signal == null) {
        return Optional.empty();
      }
      boolean available = "AVAILABLE".equals(payload.path("availability").asText());
      return Optional.of(
          new ReadSignal(signal, available, available ? payload.path("value").asText() : null));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be read", exception);
    }
  }

  record ReadSignal(StateSignal signal, boolean isAvailable, String value) {}
}
