package com.forgesync.factoryapi.adapter.inbound.observation;

import com.forgesync.factoryapi.application.InvalidObservationContractException;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ObservationContractValidator {

  private static final String SCHEMA_RESOURCE =
      "contracts/observation-envelope/v1/observation-envelope.schema.json";

  private final Schema schema;

  public ObservationContractValidator() {
    this.schema = loadSchema();
  }

  public void validate(String observationJson) {
    try {
      List<String> violations =
          schema
              .validate(
                  observationJson,
                  InputFormat.JSON,
                  executionContext ->
                      executionContext.executionConfig(
                          config -> config.formatAssertionsEnabled(true)))
              .stream()
              .map(error -> error.getInstanceLocation() + ":" + error.getKeyword())
              .sorted()
              .toList();
      if (!violations.isEmpty()) {
        throw new InvalidObservationContractException(violations);
      }
    } catch (UncheckedIOException | IllegalArgumentException exception) {
      throw new InvalidObservationContractException(List.of("MALFORMED_JSON"));
    }
  }

  private static Schema loadSchema() {
    ClassLoader classLoader = ObservationContractValidator.class.getClassLoader();
    try (InputStream stream = classLoader.getResourceAsStream(SCHEMA_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Observation contract schema is not packaged");
      }
      String schemaDocument = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(schemaDocument);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read observation contract schema", exception);
    }
  }
}
