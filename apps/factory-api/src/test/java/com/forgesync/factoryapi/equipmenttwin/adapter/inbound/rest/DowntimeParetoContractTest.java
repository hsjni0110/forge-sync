package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.DowntimeParetoProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeEvidenceKind;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoEntry;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeParetoReport;
import com.forgesync.factoryapi.equipmenttwin.domain.DowntimeReasonClassification;
import com.forgesync.factoryapi.equipmenttwin.domain.IntervalBoundaryEvidence;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationState;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DowntimeParetoContractTest {
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");

  @Test
  void goldenProjectionKeepsRankingAllConcurrentEvidenceAndUnconfirmedReasons() {
    Schema schema = schema();
    String document = readResource("fixtures/twin/v1/mazak01-downtime-pareto.json");

    assertThat(schema.validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document)
        .contains("CONCURRENT_EVIDENCE")
        .contains("UNCONFIRMED_REASON")
        .contains("ESTOP_OVERLAP")
        .contains("MODE_CHANGE")
        .contains("CONDITION_OBSERVATION");
  }

  @Test
  void responseMapperProducesTheSharedVersionedContract() throws Exception {
    DowntimeParetoResponse response =
        new DowntimeParetoResponseMapper()
            .map(
                new DowntimeParetoProcessingResult(
                    "sha256:" + "e".repeat(64),
                    Instant.parse("2026-09-10T00:00:00Z"),
                    true,
                    report()));
    String document = new ObjectMapper().writeValueAsString(response);

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
  }

  private static DowntimeParetoReport report() {
    var startEvidence = new IntervalBoundaryEvidence(1, START, "execution-1");
    var endEvidence = new IntervalBoundaryEvidence(2, START.plusSeconds(60), "execution-2");
    var evidence =
        new DowntimeEvidence(
            DowntimeEvidenceKind.CONDITION_OBSERVATION,
            "CONDITION",
            "WARNING",
            START.plusSeconds(30),
            3,
            "condition-3",
            "Mazak01-controller",
            "SYSTEM",
            "WARNING",
            "406",
            "DOOR OPEN");
    var entry =
        new DowntimeParetoEntry(
            1,
            UtilizationState.STOPPED,
            START,
            START.plusSeconds(60),
            Duration.ofSeconds(60),
            new BigDecimal("100.000000"),
            new BigDecimal("100.000000"),
            startEvidence,
            endEvidence,
            DowntimeReasonClassification.CONCURRENT_EVIDENCE,
            List.of(evidence));
    return new DowntimeParetoReport(
        "1.0.0",
        "sha256:" + "b".repeat(64),
        "sha256:" + "a".repeat(64),
        "Mazak01",
        UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac"),
        3,
        START,
        START.plusSeconds(60),
        new BigDecimal("60.000000"),
        "sha256:" + "c".repeat(64),
        "sha256:" + "d".repeat(64),
        List.of(entry));
  }

  private static Schema schema() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {})
        .getSchema(readResource("contracts/twin/v1/downtime-pareto.schema.json"));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        DowntimeParetoContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
