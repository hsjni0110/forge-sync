package com.forgesync.factoryapi.production.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.production.domain.ObservedProductionContext;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservedProductionContextContractTest {
  @Test
  void exposesObservedCountsWithoutCreatingAProductionResult() throws Exception {
    String hash = "sha256:" + "a".repeat(64);
    var evidence =
        new ObservedProductionContext.Evidence(
            1, Instant.EPOCH, "event-1", "raw-1", "Mazak01-path_1");
    var context =
        new ObservedProductionContext(
            "1.0.0",
            "Mazak01",
            "00d64db8-967e-41ba-9d09-fdd087710aac",
            10,
            hash,
            List.of(
                new ObservedProductionContext.ProgramInterval(
                    "MAIN", "AVAILABLE", "155", Instant.EPOCH, null, evidence, null)),
            List.of(
                new ObservedProductionContext.ProgramSummary(
                    "155",
                    1,
                    1,
                    new BigDecimal("60"),
                    new BigDecimal("60"),
                    new BigDecimal("60"),
                    List.of(hash))),
            0,
            new ObservedProductionContext.PartCountSummary(
                "UNAVAILABLE", null, 0, 0, 1, "NO_USABLE_TRANSITIONS", List.of()));
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new ObservedProductionContextResponseMapper().map(context));

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document).contains("\"productionResultStatus\":\"NOT_OBSERVED\"");
    assertThat(document).doesNotContain("completedQuantity", "goodQuantity", "rejectQuantity");
  }

  private static com.networknt.schema.Schema schema() throws Exception {
    try (InputStream stream =
        ObservedProductionContextContractTest.class
            .getClassLoader()
            .getResourceAsStream(
                "contracts/production/v1/observed-production-context.schema.json")) {
      assertThat(stream).isNotNull();
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
