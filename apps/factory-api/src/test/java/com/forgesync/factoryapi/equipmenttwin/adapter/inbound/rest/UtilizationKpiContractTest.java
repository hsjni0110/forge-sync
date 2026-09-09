package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgesync.factoryapi.equipmenttwin.application.UtilizationKpiProcessingResult;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeMetric;
import com.forgesync.factoryapi.equipmenttwin.domain.AccumulatedTimeObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentStateIntervalReport;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignal;
import com.forgesync.factoryapi.equipmenttwin.domain.StateSignalObservation;
import com.forgesync.factoryapi.equipmenttwin.domain.UtilizationKpiPolicy;
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

class UtilizationKpiContractTest {

  @Test
  void goldenProjectionExposesFormulasCoverageVersionsAndBothEvidencePaths() {
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {})
            .getSchema(readResource("contracts/twin/v1/utilization-kpis.schema.json"));
    String document = readResource("fixtures/twin/v1/mazak01-utilization-kpis.json");

    assertThat(schema.validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document)
        .contains("STATE_DURATION / OBSERVED_RANGE")
        .contains("AUTO_DELTA / TOTAL_DELTA")
        .contains("CUT_DELTA / AUTO_DELTA")
        .contains("OBSERVED_EXECUTION_INTERVALS")
        .contains("OBSERVED_ACCUMULATED_TIME")
        .contains("DIFFERENT_EVIDENCE_PATHS_NOT_EQUIVALENT");
  }

  @Test
  void responseMapperProducesTheSharedVersionedContract() throws Exception {
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> {})
            .getSchema(readResource("contracts/twin/v1/utilization-kpis.schema.json"));
    var report =
        new UtilizationKpiPolicy()
            .calculate("sha256:" + "a".repeat(64), intervalReport(), counters());
    UtilizationKpiResponse response =
        new UtilizationKpiResponseMapper()
            .map(
                new UtilizationKpiProcessingResult(
                    "sha256:" + "b".repeat(64),
                    Instant.parse("2026-09-08T00:00:00Z"),
                    true,
                    report));
    String document = new ObjectMapper().writeValueAsString(response);

    assertThat(schema.validate(document, InputFormat.JSON)).isEmpty();
  }

  private static EquipmentStateIntervalReport intervalReport() {
    UUID session = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
    Instant start = Instant.parse("2016-10-05T09:00:00Z");
    List<StateSignalObservation> observations =
        List.of(
            new StateSignalObservation(
                "Mazak01", session, 0, start, "event-0", StateSignal.EXECUTION, true, "READY"),
            new StateSignalObservation(
                "Mazak01",
                session,
                1,
                start.plusSeconds(300),
                "event-1",
                StateSignal.EXECUTION,
                true,
                "ACTIVE"),
            new StateSignalObservation(
                "Mazak01",
                session,
                2,
                start.plusSeconds(600),
                "event-2",
                StateSignal.CONTROLLER_MODE,
                true,
                "AUTOMATIC"));
    EquipmentStateIntervalPolicy policy = new EquipmentStateIntervalPolicy();
    return EquipmentStateIntervalReport.of(
        EquipmentStateIntervalPolicy.RULE_VERSION,
        observations,
        policy.segment(EquipmentStateIntervalPolicy.RULE_VERSION, observations));
  }

  private static List<AccumulatedTimeObservation> counters() {
    UUID session = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
    Instant start = Instant.parse("2016-10-05T09:00:00Z");
    return List.of(
        counter(session, start, AccumulatedTimeMetric.TOTAL, 0, "100"),
        counter(session, start, AccumulatedTimeMetric.AUTO, 0, "20"),
        counter(session, start, AccumulatedTimeMetric.CUT, 0, "5"),
        counter(session, start, AccumulatedTimeMetric.TOTAL, 2, "300"),
        counter(session, start, AccumulatedTimeMetric.AUTO, 2, "120"),
        counter(session, start, AccumulatedTimeMetric.CUT, 2, "45"));
  }

  private static AccumulatedTimeObservation counter(
      UUID session, Instant start, AccumulatedTimeMetric metric, long sequence, String value) {
    return new AccumulatedTimeObservation(
        "Mazak01",
        session,
        sequence,
        start.plusSeconds(sequence * 300),
        "counter-" + metric + "-" + sequence,
        metric,
        true,
        new BigDecimal(value));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        UtilizationKpiContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
