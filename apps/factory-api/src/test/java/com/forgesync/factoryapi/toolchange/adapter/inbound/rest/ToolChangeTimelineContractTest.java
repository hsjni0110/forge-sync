package com.forgesync.factoryapi.toolchange.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ToolChangeTimelineContractTest {
  @Test
  void goldenTimelineSatisfiesTheVersionedContract() {
    var schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(read("contracts/tool-changes/v1/tool-change-timeline.schema.json"));
    var fixture = read("fixtures/tool-changes/v1/mazak01-tool-changes.json");

    assertThat(schema.validate(fixture, InputFormat.JSON)).isEmpty();
    assertThat(schema.validate(fixture.replace("\"1.0.0\"", "\"2.0.0\""), InputFormat.JSON))
        .isNotEmpty();
  }

  private static String read(String name) {
    try (var stream =
        ToolChangeTimelineContractTest.class.getClassLoader().getResourceAsStream(name)) {
      if (stream == null) throw new IllegalStateException("Missing " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
