package com.forgesync.factoryapi.processanalytics.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Reads PartCount EVENT payloads without filling gaps or repairing counter reversals. */
final class CanonicalPartCountReader {
  private final ObjectMapper objectMapper;

  CanonicalPartCountReader(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  Optional<ReadPartCount> read(String document) {
    try {
      var payload = objectMapper.readTree(document).path("payload");
      if (!"PART_COUNT".equals(payload.path("eventType").asText())) return Optional.empty();
      boolean available = "AVAILABLE".equals(payload.path("availability").asText());
      BigDecimal value = available ? payload.path("value").decimalValue() : null;
      return Optional.of(new ReadPartCount(available, value));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be read", exception);
    }
  }

  record ReadPartCount(boolean isAvailable, BigDecimal value) {}
}
