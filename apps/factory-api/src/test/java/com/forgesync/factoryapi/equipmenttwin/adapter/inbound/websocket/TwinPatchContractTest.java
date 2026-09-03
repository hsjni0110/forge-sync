package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TwinPatchContractTest {

  private static final String TWIN_SCHEMA_ID =
      "https://forgesync.local/contracts/twin/v1/twin-snapshot.schema.json";
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final Schema schema = loadSchema();

  @Test
  void wholeSnapshotPatchSatisfiesTheSharedVersionedContract() throws IOException {
    JsonNode patch = patchDocument();

    assertThat(violations(patch.toString())).isEmpty();
  }

  @Test
  void rejectsMissingSnapshotAndUnsupportedContractVersion() throws IOException {
    ObjectNode patch = patchDocument();
    patch.remove("snapshot");
    assertThat(violations(patch.toString())).isNotEmpty();

    patch = patchDocument();
    patch.put("schemaVersion", "2.0.0");
    assertThat(violations(patch.toString())).isNotEmpty();
  }

  @Test
  void messageRejectsCrossFieldVersionMismatch() throws IOException {
    TwinSnapshotResponse snapshot =
        objectMapper.readValue(
            readResource("fixtures/twin/v1/mazak01-operational-twin.json"),
            TwinSnapshotResponse.class);

    assertThatThrownBy(
            () ->
                new TwinPatchMessage(
                    "1.1.0",
                    "TWIN_PATCH",
                    "Mazak01",
                    4,
                    5,
                    Instant.parse("2026-09-02T01:02:04Z"),
                    snapshot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inconsistent");
  }

  private ObjectNode patchDocument() throws IOException {
    return (ObjectNode)
        objectMapper.readTree(readResource("fixtures/twin/v1/mazak01-twin-patch.json"));
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static Schema loadSchema() {
    String twinSchema = readResource("contracts/twin/v1/twin-snapshot.schema.json");
    SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(Map.of(TWIN_SCHEMA_ID, twinSchema)));
    return registry.getSchema(readResource("contracts/websocket/v1/twin-patch.schema.json"));
  }

  private static String readResource(String path) {
    try (InputStream stream =
        TwinPatchContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read resource: " + path, exception);
    }
  }
}
