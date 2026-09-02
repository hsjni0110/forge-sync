package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot;
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
            TwinConsistencyState.PARTIAL,
            List.of(
                "metrics.spindleSpeeds",
                "state.execution",
                "metrics.toolNumber",
                "metrics.program"),
            ConnectivityState.UNKNOWN,
            ExecutionState.UNKNOWN,
            HealthState.UNKNOWN,
            FreshnessState.FRESH,
            projectedAt.plusSeconds(1),
            Duration.ofSeconds(1),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            List.of());
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new TwinSnapshotResponseMapper().map(snapshot));

    assertThat(violations(document)).isEmpty();
  }

  @Test
  void rejectsWrongVersionMissingProvenanceAndUnavailableValue() {
    String valid = readResource("fixtures/twin/v1/mazak01-operational-twin.json");

    assertThat(violations(valid.replace("\"1.0.0\"", "\"2.0.0\""))).isNotEmpty();
    assertThat(violations(valid.replaceFirst("\"provenance\": \\{", "\"lineage\": {")))
        .isNotEmpty();
    assertThat(violations(valid.replaceFirst("\"AVAILABLE\"", "\"UNAVAILABLE\""))).isNotEmpty();
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static Schema loadSchema() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
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
