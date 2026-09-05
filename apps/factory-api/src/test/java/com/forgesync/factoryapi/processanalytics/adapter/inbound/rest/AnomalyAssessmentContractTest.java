package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.processanalytics.application.AnomalyAssessmentProcessingResult;
import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessment;
import com.forgesync.factoryapi.processanalytics.domain.AssessmentDataStatus;
import com.forgesync.factoryapi.processanalytics.domain.CycleBaseline;
import com.forgesync.factoryapi.processanalytics.domain.FeatureBaseline;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalyAssessmentContractTest {
  private final Schema schema =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(
              readResource("contracts/process-analytics/v1/anomaly-assessments.schema.json"));

  @Test
  void reviewedFixtureAndRestMapperSatisfyTheVersionedContract() throws Exception {
    assertThat(
            violations(
                readResource("fixtures/process-analytics/v1/mazak01-anomaly-assessments.json")))
        .isEmpty();
    String mapped =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new AnomalyAssessmentResponseMapper().map(processingResult(true)));
    assertThat(violations(mapped)).isEmpty();
  }

  static AnomalyAssessmentProcessingResult processingResult(boolean created) {
    String hash1 = "sha256:" + "1".repeat(64);
    String hash2 = "sha256:" + "2".repeat(64);
    String hash3 = "sha256:" + "3".repeat(64);
    String hash4 = "sha256:" + "4".repeat(64);
    String hash5 = "sha256:" + "5".repeat(64);
    String hash6 = "sha256:" + "6".repeat(64);
    Instant observedAt = Instant.parse("2016-10-05T09:00:00Z");
    var range =
        new ObservationRange(
            UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac"),
            1,
            2,
            observedAt,
            observedAt.plusSeconds(10),
            "event-1",
            "event-2");
    var baseline =
        new CycleBaseline(
            hash5,
            "Mazak01",
            "155",
            "1.0.0",
            "1.0.0",
            hash4,
            List.of(),
            null,
            null,
            List.of(),
            List.of(
                new FeatureBaseline(
                    "durationSeconds",
                    new BigDecimal("10.000000"),
                    null,
                    null,
                    null,
                    null,
                    0,
                    List.of(),
                    "MINIMUM_SAMPLE_COUNT_NOT_MET")));
    var provenance =
        new ObservationProvenance(
            "REAL", "NIST", "nist-mazak01-20161005", hash6, "raw-1", "2.0.0", "execution");
    var assessment =
        new AnomalyAssessment(
            hash3,
            hash6,
            hash4,
            AssessmentDataStatus.INSUFFICIENT_DATA,
            null,
            null,
            baseline,
            observedAt,
            range,
            List.of(provenance),
            List.of(),
            List.of(),
            hash2);
    return new AnomalyAssessmentProcessingResult(
        hash1,
        hash2,
        hash3,
        "Mazak01",
        "1.0.0",
        "1.0.0",
        "1.0.0",
        hash4,
        hash5,
        Instant.parse("2026-09-04T03:00:00Z"),
        created,
        List.of(assessment));
  }

  private List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        AnomalyAssessmentContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Resource is not packaged: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
