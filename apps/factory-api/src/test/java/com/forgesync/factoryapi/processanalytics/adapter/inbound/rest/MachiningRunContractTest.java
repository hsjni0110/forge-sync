package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.processanalytics.application.MachiningRunProcessingResult;
import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunStatus;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;
import com.forgesync.factoryapi.processanalytics.domain.ProcessSignal;
import com.forgesync.factoryapi.processanalytics.domain.SegmentationConfidence;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachiningRunContractTest {

  private final Schema schema =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(readResource("contracts/process-analytics/v1/machining-runs.schema.json"));

  @Test
  void sharedGoldenResultSatisfiesTheVersionedContract() {
    assertThat(
            violations(readResource("fixtures/process-analytics/v1/mazak01-machining-runs.json")))
        .isEmpty();
  }

  @Test
  void restMapperProducesTheSharedContract() throws JsonProcessingException {
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new MachiningRunResponseMapper().map(processingResult()));

    assertThat(violations(document)).isEmpty();
  }

  @Test
  void rejectsWrongOriginMissingEvidenceAndUnknownFields() {
    String valid = readResource("fixtures/process-analytics/v1/mazak01-machining-runs.json");

    assertThat(violations(valid.replaceFirst("DERIVED", "OBSERVED"))).isNotEmpty();
    assertThat(violations(valid.replaceFirst("\"startEvidence\"", "\"startProof\""))).isNotEmpty();
    assertThat(
            violations(
                valid.replaceFirst("\"schemaVersion\"", "\"extra\": true, \"schemaVersion\"")))
        .isNotEmpty();
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  static MachiningRunProcessingResult processingResult() {
    String processingRunId = "sha256:" + "1".repeat(64);
    UUID session = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
    Instant startedAt = Instant.parse("2016-10-05T09:18:30.447Z");
    Instant endedAt = Instant.parse("2016-10-05T09:20:59.181Z");
    ObservationProvenance provenance =
        new ObservationProvenance(
            "REAL",
            "NIST",
            "nist-mazak01-20161005",
            "sha256:" + "a".repeat(64),
            "raw-active",
            "2.0.0",
            "Mazak01-path_13");
    BoundaryEvidence start =
        new BoundaryEvidence("START", ProcessSignal.EXECUTION, "active", 42, startedAt, provenance);
    BoundaryEvidence end =
        new BoundaryEvidence("END", ProcessSignal.EXECUTION, "ready", 45, endedAt, provenance);
    MachiningRun run =
        new MachiningRun(
            "sha256:" + "4".repeat(64),
            processingRunId,
            "1.0.0",
            "Mazak01",
            MachiningRunStatus.COMPLETED,
            "155",
            startedAt,
            endedAt,
            SegmentationConfidence.HIGH,
            List.of("EXECUTION_START_CONFIRMED", "PROGRAM_OBSERVED", "POSITIVE_SPINDLE_OBSERVED"),
            new ObservationRange(session, 42, 45, startedAt, endedAt, "active", "ready"),
            start,
            end,
            List.of(),
            "sha256:" + "5".repeat(64));
    return new MachiningRunProcessingResult(
        processingRunId,
        "Mazak01",
        session,
        45,
        "1.0.0",
        "sha256:" + "2".repeat(64),
        4,
        "sha256:" + "3".repeat(64),
        Instant.parse("2026-09-04T01:02:03Z"),
        true,
        List.of(run));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        MachiningRunContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
