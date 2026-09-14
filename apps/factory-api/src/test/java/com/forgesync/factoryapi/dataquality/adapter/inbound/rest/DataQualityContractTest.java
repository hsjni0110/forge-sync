package com.forgesync.factoryapi.dataquality.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.dataquality.adapter.outbound.resource.ClasspathDataQualityEvidence;
import com.forgesync.factoryapi.dataquality.application.DataQualityReportAssembler;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DataQualityContractTest {
  @Test
  void goldenReportKeepsDimensionsSeparateAndHasNoOverallGrade() {
    var schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(read("contracts/data-quality/v1/data-quality-report.schema.json"));
    String fixture = read("fixtures/data-quality/v1/mazak01-data-quality.json");

    assertThat(schema.validate(fixture, InputFormat.JSON)).isEmpty();
    assertThat(fixture).contains("\"overallGrade\": null").contains("\"NOT_EVALUATED\"");
  }

  @Test
  void sourceOnlyResponseAlsoSatisfiesTheContractWithoutInventingRuntimeQuality() throws Exception {
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(
                new DataQualityReportAssembler()
                    .assembleSource(
                        new ClasspathDataQualityEvidence().load(),
                        Instant.parse("2026-09-13T09:00:00Z")));

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document).contains("\"runtime\":{\"status\":\"NOT_EVALUATED\"");
  }

  private static com.networknt.schema.Schema schema() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(read("contracts/data-quality/v1/data-quality-report.schema.json"));
  }

  private static String read(String name) {
    try (var stream = DataQualityContractTest.class.getClassLoader().getResourceAsStream(name)) {
      if (stream == null) throw new IllegalStateException("Missing " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
