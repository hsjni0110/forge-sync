package com.forgesync.factoryapi.toolpath.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.toolpath.domain.AxisPositionObservation;
import com.forgesync.factoryapi.toolpath.domain.ObservedEnvelope;
import com.forgesync.factoryapi.toolpath.domain.ObservedToolpath;
import com.forgesync.factoryapi.toolpath.domain.ToolpathAvailability;
import com.forgesync.factoryapi.toolpath.domain.ToolpathPoint;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservedToolpathContractTest {
  @Test
  void goldenToolpathSatisfiesTheVersionedContract() {
    var schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(read("contracts/toolpath/v1/observed-toolpath.schema.json"));
    var fixture = read("fixtures/toolpath/v1/mazak01-observed-toolpath.json");

    assertThat(schema.validate(fixture, InputFormat.JSON)).isEmpty();
    assertThat(schema.validate(fixture.replace("\"1.0.0\"", "\"2.0.0\""), InputFormat.JSON))
        .isNotEmpty();
  }

  @Test
  void controllerSerializesAnAvailablePathWithoutANullReason() throws Exception {
    var source =
        new AxisPositionObservation(
            "X",
            10,
            Instant.parse("2016-10-05T09:00:00Z"),
            "AVAILABLE",
            1.0,
            "MILLIMETER",
            "Mazak01-X_1",
            "nist",
            "artifact",
            "raw",
            "2.1.0");
    var points =
        List.of(
            new ToolpathPoint(
                10,
                source.sourceObservedAt(),
                List.of(1.0, 2.0, 3.0),
                List.of(source, source, source)),
            new ToolpathPoint(
                11,
                source.sourceObservedAt().plusMillis(100),
                List.of(2.0, 3.0, 4.0),
                List.of(source, source, source)));
    var controller =
        new ObservedToolpathController(
            (machine, session, start, end, through) ->
                new ObservedToolpath(
                    ToolpathAvailability.AVAILABLE,
                    null,
                    points,
                    new ObservedEnvelope(List.of(1.0, 2.0, 3.0), List.of(2.0, 3.0, 4.0))));
    var response =
        controller
            .find("Mazak01", UUID.fromString("10000000-0000-4000-8000-000000000001"), 10, 11, 11)
            .getBody();
    var document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(response);

    assertThat(document).doesNotContain("\"reason\"");
  }

  private static String read(String name) {
    try (var stream =
        ObservedToolpathContractTest.class.getClassLoader().getResourceAsStream(name)) {
      if (stream == null) throw new IllegalStateException("Missing " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
