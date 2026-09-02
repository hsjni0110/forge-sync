package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservationKind;
import java.util.Objects;

final class CanonicalEquipmentStateReader {

  private final ObjectMapper objectMapper;

  CanonicalEquipmentStateReader(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  StateObservation read(String canonicalEnvelope) {
    try {
      JsonNode document = objectMapper.readTree(canonicalEnvelope);
      StateObservationKind kind =
          StateObservationKind.valueOf(document.path("observationKind").asText());
      JsonNode payload = document.path("payload");
      return switch (kind) {
        case SAMPLE -> readAvailablePayload(kind, payload, "metric");
        case EVENT -> readAvailablePayload(kind, payload, "eventType");
        case CONDITION -> readCondition(payload);
      };
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Validated Canonical Observation cannot be projected", exception);
    }
  }

  private static StateObservation readAvailablePayload(
      StateObservationKind kind, JsonNode payload, String semanticTypeField) {
    ObservationAvailability availability =
        ObservationAvailability.valueOf(payload.path("availability").asText());
    String value =
        availability == ObservationAvailability.AVAILABLE ? payload.path("value").asText() : null;
    return new StateObservation(
        kind, payload.path(semanticTypeField).asText(), availability, value);
  }

  private static StateObservation readCondition(JsonNode payload) {
    String level = payload.path("level").asText();
    ObservationAvailability availability =
        level.equals("UNAVAILABLE")
            ? ObservationAvailability.UNAVAILABLE
            : ObservationAvailability.AVAILABLE;
    return new StateObservation(
        StateObservationKind.CONDITION,
        payload.path("conditionType").asText(),
        availability,
        availability == ObservationAvailability.AVAILABLE ? level : null);
  }
}
