package com.forgesync.factoryapi.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.alarm.domain.ConditionObservation;
import com.forgesync.factoryapi.application.ValidatedObservationMessage;
import java.util.Objects;
import java.util.Optional;

final class CanonicalConditionObservationMapper {
  private final ObjectMapper objectMapper;

  CanonicalConditionObservationMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  Optional<ConditionObservation> map(ValidatedObservationMessage observation) {
    if (!observation.observationKind().equals("CONDITION")) return Optional.empty();
    try {
      JsonNode payload = objectMapper.readTree(observation.observationJson()).path("payload");
      return Optional.of(
          new ConditionObservation(
              observation.machineId(),
              observation.replaySessionId(),
              observation.replaySequence(),
              observation.sourceObservedAt(),
              observation.sourceEventKey(),
              observation.sourceDataItemId(),
              observation.componentId(),
              payload.path("conditionType").asText(),
              payload.path("level").asText(),
              nullableText(payload, "nativeCode"),
              nullableText(payload, "message")));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Validated Condition cannot be read", exception);
    }
  }

  private static String nullableText(JsonNode payload, String field) {
    JsonNode value = payload.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
