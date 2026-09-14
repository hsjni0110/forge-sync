package com.forgesync.factoryapi.processanalytics.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendPolicy;
import com.forgesync.factoryapi.processanalytics.domain.ToolLoadTrendReport;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolLoadTrendContractTest {
  @Test
  void acceptsDerivedLoadTrendWithoutPhysicalWearOrControlClaims() throws Exception {
    String hash = "sha256:" + "a".repeat(64);
    String document =
        """
        {
          "schemaVersion":"1.0.0","policyVersion":"1.0.0","machineId":"Mazak01",
          "replaySessionId":"00d64db8-967e-41ba-9d09-fdd087710aac",
          "throughReplaySequence":42,"machiningRunProcessingRunId":"%s",
          "policy":{"minimumRawSamplesPerPoint":3,"minimumTrendPoints":5,
            "baselinePointCount":3,"minimumCoverageRatio":0.8,
            "pointFormula":"median","coverageFormula":"eligible observed points / observed candidate points","deviationFormula":"relative difference",
            "slopeFormula":"OLS by point ordinal"},
          "provenance":{"origin":"DERIVED","sourceKind":"REAL","provider":"NIST",
            "sourceSetId":"nist-mazak01-20161005"},
          "groups":[]
        }
        """
            .formatted(hash);

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document)
        .doesNotContain("wearPercent", "remainingUsefulLife", "machineFault", "alarm");
  }

  @Test
  void mapperOmitsUnavailableTrendNumbersAndSatisfiesTheSharedContract() throws Exception {
    Instant observedAt = Instant.parse("2016-10-05T10:00:10Z");
    var report =
        new ToolLoadTrendPolicy()
            .project(
                "Mazak01",
                "00d64db8-967e-41ba-9d09-fdd087710aac",
                42,
                "sha256:" + "a".repeat(64),
                List.of(
                    new ToolLoadTrendReport.MachiningRunInput(
                        "sha256:" + "b".repeat(64),
                        "155",
                        "COMPLETED",
                        observedAt.minusSeconds(10),
                        observedAt.plusSeconds(10))),
                List.of(
                    new ToolLoadTrendReport.ToolNumberObservation(
                        true, 4, 1, observedAt.minusSeconds(5))),
                List.of(
                    new ToolLoadTrendReport.LoadObservation(
                        true,
                        BigDecimal.TEN,
                        "PERCENT",
                        "Mazak01-C",
                        2,
                        observedAt,
                        "event-2",
                        "raw-2",
                        "Mazak01-C_2",
                        "2.3.0",
                        "REAL",
                        "NIST",
                        "nist-mazak01-20161005")));
    String document =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(new ToolLoadTrendResponseMapper().map(report));

    assertThat(schema().validate(document, InputFormat.JSON)).isEmpty();
    assertThat(document).contains("\"status\":\"INSUFFICIENT_SAMPLES\"");
    assertThat(document).doesNotContain("latestDeviationPercent", "slopePercentPerPoint");
  }

  private static com.networknt.schema.Schema schema() throws Exception {
    try (InputStream stream =
        ToolLoadTrendContractTest.class
            .getClassLoader()
            .getResourceAsStream("contracts/process-analytics/v1/tool-load-trends.schema.json")) {
      assertThat(stream).isNotNull();
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
