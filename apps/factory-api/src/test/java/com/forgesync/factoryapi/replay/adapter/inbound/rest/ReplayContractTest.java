package com.forgesync.factoryapi.replay.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReplayContractTest {
  private final Schema schema =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(readResource("contracts/replay/v1/replay-session.schema.json"));

  @Test
  void acceptsSharedReplaySessionAndRejectsUnsupportedSpeed() {
    String valid = readResource("fixtures/replay/v1/running-replay-session.json");

    assertThat(violations(valid)).isEmpty();
    assertThat(violations(valid.replace("\"speedMultiplier\": 10", "\"speedMultiplier\": 2")))
        .isNotEmpty();
  }

  private java.util.List<com.networknt.schema.Error> violations(String document) {
    return schema.validate(
        document,
        InputFormat.JSON,
        context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
  }

  private static String readResource(String path) {
    try (InputStream stream = ReplayContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Resource is not packaged: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
