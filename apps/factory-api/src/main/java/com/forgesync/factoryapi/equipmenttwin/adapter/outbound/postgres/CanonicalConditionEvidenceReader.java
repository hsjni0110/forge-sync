package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;

final class CanonicalConditionEvidenceReader {
  private final ObjectMapper objectMapper;

  CanonicalConditionEvidenceReader(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  Optional<ReadCondition> read(String canonicalEnvelope) {
    try {
      JsonNode document = objectMapper.readTree(canonicalEnvelope);
      if (!"CONDITION".equals(document.path("observationKind").asText())) {
        return Optional.empty();
      }
      JsonNode payload = document.path("payload");
      String level = payload.path("level").asText();
      if (!level.equals("WARNING") && !level.equals("FAULT")) {
        return Optional.empty();
      }
      return Optional.of(
          new ReadCondition(
              document.path("subject").path("componentId").asText(),
              payload.path("conditionType").asText(),
              level,
              nullableText(payload, "nativeCode"),
              nullableText(payload, "message")));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Condition cannot be read", exception);
    }
  }

  private static String nullableText(JsonNode payload, String field) {
    JsonNode value = payload.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  record ReadCondition(
      String componentId, String conditionType, String level, String nativeCode, String message) {}
}
