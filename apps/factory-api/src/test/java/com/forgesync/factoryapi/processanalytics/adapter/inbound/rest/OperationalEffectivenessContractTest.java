package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.processanalytics.application.OperationalEffectivenessProcessingResult;
import com.forgesync.factoryapi.processanalytics.domain.AvailabilityComponent;
import com.forgesync.factoryapi.processanalytics.domain.ComponentStatus;
import com.forgesync.factoryapi.processanalytics.domain.OperationalEffectivenessReport;
import com.forgesync.factoryapi.processanalytics.domain.PerformanceComponent;
import com.forgesync.factoryapi.processanalytics.domain.ThroughputComponent;
import com.forgesync.factoryapi.processanalytics.domain.UnavailableComponent;
import com.forgesync.factoryapi.processanalytics.domain.ValueProvenance;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalEffectivenessContractTest {
  @Test
  void mapperOmitsUnavailableNumbersAndSatisfiesTheSharedContract() throws Exception {
    String hash = "sha256:" + "a".repeat(64);
    var unavailable =
        new UnavailableComponent(
            ComponentStatus.UNAVAILABLE,
            null,
            ValueProvenance.UNAVAILABLE,
            "QUALITY_SOURCE_NOT_AVAILABLE");
    var report =
        new OperationalEffectivenessReport(
            "1.0.0",
            "Mazak01",
            UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac"),
            10,
            Instant.parse("2016-10-05T09:00:00Z"),
            Instant.parse("2016-10-05T10:00:00Z"),
            hash,
            hash,
            hash,
            null,
            null,
            hash,
            hash,
            new AvailabilityComponent(
                ComponentStatus.AVAILABLE,
                new BigDecimal("70"),
                ValueProvenance.OBSERVED,
                ValueProvenance.DERIVED,
                "ACTIVE_DURATION / OBSERVED_RANGE",
                null),
            new PerformanceComponent(
                ComponentStatus.UNAVAILABLE,
                null,
                null,
                null,
                "HISTORICAL_MEDIAN",
                ValueProvenance.UNAVAILABLE,
                0,
                List.of(),
                "MINIMUM_SAMPLE_COUNT_NOT_MET"),
            new ThroughputComponent(
                ComponentStatus.UNAVAILABLE, null, 0, 0, 4, "NO_USABLE_TRANSITIONS"),
            unavailable,
            new UnavailableComponent(
                ComponentStatus.UNAVAILABLE,
                null,
                ValueProvenance.UNAVAILABLE,
                "QUALITY_COMPONENT_UNAVAILABLE"));
    var mapped =
        new OperationalEffectivenessResponseMapper()
            .map(
                new OperationalEffectivenessProcessingResult(
                    hash, Instant.parse("2026-09-10T00:00:00Z"), true, report));
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(mapped);

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document).doesNotContain("\"compositeOee\":{\"status\":\"UNAVAILABLE\",\"percent\"");
  }

  private static com.networknt.schema.Schema schema() throws Exception {
    try (InputStream stream =
        OperationalEffectivenessContractTest.class
            .getClassLoader()
            .getResourceAsStream(
                "contracts/process-analytics/v1/operational-effectiveness.schema.json")) {
      assertThat(stream).isNotNull();
      String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(source);
    }
  }
}
