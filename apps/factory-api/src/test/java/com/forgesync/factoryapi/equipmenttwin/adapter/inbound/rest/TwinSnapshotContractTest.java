package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.TwinMetrics;
import com.forgesync.factoryapi.equipmenttwin.application.ReplayCursor;
import com.forgesync.factoryapi.equipmenttwin.application.TwinConsistencyState;
import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TwinSnapshotContractTest {

  /**
   * One WebSocket patch carries a whole snapshot, so the snapshot size is the patch budget. The
   * ceiling is deliberately loose here; Step 47 measures the achieved publish rate and narrows it.
   */
  private static final int MAX_SNAPSHOT_BYTES = 64 * 1024;

  private final Schema schema = loadSchema();

  @Test
  void goldenOperationalTwinSatisfiesTheSharedVersionedContract() {
    String snapshot = readResource("fixtures/twin/v1/mazak01-operational-twin.json");

    assertThat(violations(snapshot)).isEmpty();
  }

  @Test
  void restMapperProducesTheSameSharedContract() throws JsonProcessingException {
    Instant projectedAt = Instant.parse("2026-09-02T01:02:04Z");
    OperationalTwinSnapshot snapshot =
        new OperationalTwinSnapshot(
            "Mazak01",
            new TwinVersion(1),
            projectedAt,
            new ReplayCursor(
                new java.util.UUID(0, 0), 0, projectedAt, projectedAt, new TwinVersion(1)),
            TwinConsistencyState.PARTIAL,
            List.of(
                "metrics.spindleSpeeds",
                "metrics.bAxisAngle",
                "state.execution",
                "state.health",
                "metrics.toolNumber",
                "metrics.program"),
            ConnectivityState.UNKNOWN,
            ExecutionState.UNKNOWN,
            HealthState.UNKNOWN,
            FreshnessState.FRESH,
            projectedAt.plusSeconds(1),
            Duration.ofSeconds(1),
            2_000,
            10_000,
            List.of(),
            List.of(),
            List.of(),
            TwinMetrics.none(),
            List.of(),
            Optional.empty());
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new TwinSnapshotResponseMapper().map(snapshot));

    assertThat(violations(document)).isEmpty();
    assertThat(document).contains("\"schemaVersion\":\"1.6.0\"").contains("\"axisPositions\"");
  }

  @Test
  void goldenTwinKeepsEveryObservedChannelSeparateAndNeverZeroFills() throws IOException {
    com.fasterxml.jackson.databind.JsonNode metrics =
        new ObjectMapper()
            .readTree(readResource("fixtures/twin/v1/mazak01-operational-twin.json"))
            .path("metrics");

    assertThat(metrics.path("loads"))
        .extracting(
            load -> load.path("observation").path("componentId").asText(),
            load ->
                load.path("provenance").path("transformation").path("sourceDataItemId").asText())
        .containsExactly(
            tuple("Mazak01-B", "Mazak01-B_1"),
            tuple("Mazak01-C", "Mazak01-C_2"),
            tuple("Mazak01-C2", "Mazak01-C2_1"),
            tuple("Mazak01-X", "Mazak01-X_3"),
            tuple("Mazak01-Y", "Mazak01-Y_3"),
            tuple("Mazak01-Z", "Mazak01-Z_3"));
    assertThat(metrics.path("loads").path(4).has("value")).isFalse();
    assertThat(metrics.path("temperatures"))
        .extracting(
            item ->
                item.path("provenance").path("transformation").path("sourceDataItemId").asText())
        .containsExactly("Mazak01-C_7", "Mazak01-C2_3");
    assertThat(metrics.path("pathFeedrate").path("unit").asText()).isEqualTo("MILLIMETER/SECOND");
    assertThat(metrics.path("partCount").path("value").asInt()).isEqualTo(17);
    assertThat(metrics.path("controllerMode").path("value").asText()).isEqualTo("AUTOMATIC");
    assertThat(metrics.path("powerState").path("value").asText()).isEqualTo("ON");
  }

  @Test
  void earlierSnapshotWithoutTheNewOptionalChannelsStaysValid() {
    String withoutNewChannels = readResource("fixtures/twin/v1/mazak01-twin-patch.json");

    assertThat(violations(snapshotOf(withoutNewChannels))).isEmpty();
  }

  @Test
  void rejectsAChannelUnitThatTheSourceNeverDeclared() {
    String valid = readResource("fixtures/twin/v1/mazak01-operational-twin.json");

    assertThat(violations(valid.replace("\"unit\": \"PERCENT\"", "\"unit\": \"NEWTON\"")))
        .isNotEmpty();
    assertThat(violations(valid.replace("\"unit\": \"CELSIUS\"", "\"unit\": \"FAHRENHEIT\"")))
        .isNotEmpty();
  }

  @Test
  void oneFullyPopulatedSnapshotStaysInsideThePublishedPayloadBudget() {
    String golden = readResource("fixtures/twin/v1/mazak01-operational-twin.json");

    assertThat(compact(golden).getBytes(StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(MAX_SNAPSHOT_BYTES);
  }

  @Test
  void rejectsWrongVersionMissingProvenanceAndUnavailableValue() {
    String valid = readResource("fixtures/twin/v1/mazak01-operational-twin.json");

    assertThat(violations(valid.replace("\"1.6.0\"", "\"2.0.0\""))).isNotEmpty();
    assertThat(violations(valid.replaceFirst("\"provenance\": \\{", "\"lineage\": {")))
        .isNotEmpty();
    assertThat(violations(valid.replaceFirst("\"AVAILABLE\"", "\"UNAVAILABLE\""))).isNotEmpty();
  }

  private static String snapshotOf(String patch) {
    try {
      return new ObjectMapper().readTree(patch).path("snapshot").toString();
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read the patch fixture", exception);
    }
  }

  private static String compact(String document) {
    try {
      return new ObjectMapper().readTree(document).toString();
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read the snapshot fixture", exception);
    }
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static Schema loadSchema() {
    String cursorSchema = readResource("contracts/replay/v1/replay-cursor.schema.json");
    return SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder ->
                builder.schemas(
                    java.util.Map.of(
                        "https://forgesync.local/contracts/replay/v1/replay-cursor.schema.json",
                        cursorSchema)))
        .getSchema(readResource("contracts/twin/v1/twin-snapshot.schema.json"));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        TwinSnapshotContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
