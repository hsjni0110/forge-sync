package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureProcessingResult;
import com.forgesync.factoryapi.processanalytics.application.CycleFeatureSet;
import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import com.forgesync.factoryapi.processanalytics.domain.CycleObservation;
import com.forgesync.factoryapi.processanalytics.domain.CycleSignal;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CycleFeatureContractTest {
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private final Schema schema =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(readResource("contracts/process-analytics/v1/cycle-features.schema.json"));

  @Test
  void sharedGoldenResultSatisfiesTheVersionedContract() {
    assertThat(
            violations(readResource("fixtures/process-analytics/v1/mazak01-cycle-features.json")))
        .isEmpty();
  }

  @Test
  void restMapperProducesTheSharedContractShape() throws JsonProcessingException {
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new CycleFeatureResponseMapper().map(processingResult(true)));

    assertThat(violations(document)).isEmpty();
  }

  @Test
  void rejectsWrongOriginAndUnknownFields() {
    String valid = readResource("fixtures/process-analytics/v1/mazak01-cycle-features.json");
    assertThat(violations(valid.replaceFirst("DERIVED", "OBSERVED"))).isNotEmpty();
    assertThat(
            violations(valid.replaceFirst("\"schemaVersion\"", "\"extra\":true,\"schemaVersion\"")))
        .isNotEmpty();
  }

  static CycleFeatureProcessingResult processingResult(boolean created) {
    ObservationProvenance provenance = provenance();
    ObservationRange range =
        new ObservationRange(SESSION, 1, 5, START, START.plusSeconds(7), "active", "hold");
    BoundaryEvidence evidence =
        new BoundaryEvidence("START", ProcessSignal.EXECUTION, "active", 1, START, provenance);
    MachiningRun run =
        new MachiningRun(
            "sha256:" + "6".repeat(64),
            "sha256:" + "2".repeat(64),
            "1.0.0",
            "Mazak01",
            MachiningRunStatus.COMPLETED,
            "155",
            START,
            START.plusSeconds(10),
            SegmentationConfidence.HIGH,
            List.of("EXECUTION_BOUNDARY"),
            range,
            evidence,
            evidence,
            List.of(),
            "sha256:" + "8".repeat(64));
    List<CycleObservation> observations =
        List.of(
            observation(1, 0, CycleSignal.EXECUTION, "ACTIVE", null, null, "execution"),
            observation(2, 0, CycleSignal.SPINDLE_SPEED, null, "0", "REVOLUTION/MINUTE", "rpm"),
            observation(3, 2, CycleSignal.SPINDLE_SPEED, null, "100", "REVOLUTION/MINUTE", "rpm"),
            observation(4, 6, CycleSignal.SPINDLE_SPEED, null, "200", "REVOLUTION/MINUTE", "rpm"),
            observation(5, 7, CycleSignal.EXECUTION, "FEED_HOLD", null, null, "execution"));
    var feature = new CycleFeatureExtractor().extract(run, observations);
    return new CycleFeatureProcessingResult(
        "sha256:" + "1".repeat(64),
        "sha256:" + "2".repeat(64),
        "Mazak01",
        "1.0.0",
        "sha256:" + "3".repeat(64),
        5,
        1,
        "sha256:" + "4".repeat(64),
        Instant.parse("2026-09-04T02:00:00Z"),
        created,
        List.of(new CycleFeatureSet("sha256:" + "5".repeat(64), feature)));
  }

  private static CycleObservation observation(
      long sequence,
      long offset,
      CycleSignal signal,
      String text,
      String number,
      String unit,
      String sourceDataItemId) {
    return new CycleObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(offset),
        "event-" + sequence,
        signal,
        signal.isMetric() ? "Mazak01-spindle" : "Mazak01-controller",
        sourceDataItemId,
        unit,
        true,
        text,
        number == null ? null : new BigDecimal(number),
        provenance());
  }

  private static ObservationProvenance provenance() {
    return new ObservationProvenance(
        "REAL",
        "NIST",
        "nist-mazak01-20161005",
        "sha256:" + "a".repeat(64),
        "raw-record",
        "2.0.0",
        "data-item");
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        CycleFeatureContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Resource is not packaged: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
