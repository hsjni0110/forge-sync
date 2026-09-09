package com.forgesync.factoryapi.equipmenttwin.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeMetric;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads accumulated-time SAMPLE payloads without repairing resets or values. */
final class CanonicalAccumulatedTimeReader {

  private static final Map<String, AccumulatedTimeMetric> METRICS =
      Map.of(
          "TOTAL_ACCUMULATED_TIME", AccumulatedTimeMetric.TOTAL,
          "AUTO_ACCUMULATED_TIME", AccumulatedTimeMetric.AUTO,
          "CUT_ACCUMULATED_TIME", AccumulatedTimeMetric.CUT);

  private final ObjectMapper objectMapper;

  CanonicalAccumulatedTimeReader(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  Optional<ReadCounter> read(String canonicalEnvelope) {
    try {
      JsonNode payload = objectMapper.readTree(canonicalEnvelope).path("payload");
      AccumulatedTimeMetric metric = METRICS.get(payload.path("metric").asText(""));
      if (metric == null) {
        return Optional.empty();
      }
      boolean available = "AVAILABLE".equals(payload.path("availability").asText());
      BigDecimal value = available ? payload.path("value").decimalValue() : null;
      return Optional.of(new ReadCounter(metric, available, value));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored Canonical Observation cannot be read", exception);
    }
  }

  record ReadCounter(AccumulatedTimeMetric metric, boolean isAvailable, BigDecimal valueSeconds) {}
}
