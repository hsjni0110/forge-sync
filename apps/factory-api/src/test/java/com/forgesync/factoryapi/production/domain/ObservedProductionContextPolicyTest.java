package com.forgesync.factoryapi.production.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservedProductionContextPolicyTest {
  private static final Instant START = Instant.parse("2016-10-05T10:00:00Z");

  @Test
  void keepsMainAndSubprogramIntervalsSeparateAndDoesNotAssignUnknownRuns() {
    var report =
        new ObservedProductionContextPolicy()
            .project(
                "Mazak01",
                "session-1",
                30,
                "sha256:" + "a".repeat(64),
                List.of(
                    program(1, "MAIN", "155", true),
                    program(2, "MAIN", "155", true),
                    program(3, "SUBPROGRAM", null, false),
                    program(20, "MAIN", null, false)),
                List.of(
                    run("run-1", "155", 5, 65, "COMPLETED"),
                    run("run-2", "155", 70, 190, "COMPLETED"),
                    run("run-unknown", null, 200, 230, "INTERRUPTED")),
                List.of());

    assertThat(report.programIntervals()).hasSize(3);
    assertThat(report.programIntervals().get(0).kind()).isEqualTo("MAIN");
    assertThat(report.programIntervals().get(0).endedAt()).isEqualTo(START.plusSeconds(20));
    assertThat(report.programIntervals().get(1).kind()).isEqualTo("MAIN");
    assertThat(report.programIntervals().get(1).availability()).isEqualTo("UNAVAILABLE");
    assertThat(report.programIntervals().get(2).kind()).isEqualTo("SUBPROGRAM");
    assertThat(report.programSummaries())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.programName()).isEqualTo("155");
              assertThat(summary.completedRunCount()).isEqualTo(2);
              assertThat(summary.totalDurationSeconds()).isEqualByComparingTo("180");
              assertThat(summary.meanDurationSeconds()).isEqualByComparingTo("90.000000");
              assertThat(summary.medianDurationSeconds()).isEqualByComparingTo("90.000000");
            });
    assertThat(report.unassignedRunCount()).isEqualTo(1);
  }

  @Test
  void countsOnlyNonNegativeAdjacentPartCountChangesAndCallsOverlapNonCausal() {
    var report =
        new ObservedProductionContextPolicy()
            .project(
                "Mazak01",
                "session-1",
                30,
                "sha256:" + "a".repeat(64),
                List.of(),
                List.of(run("run-1", "155", 0, 100, "COMPLETED")),
                List.of(
                    part(1, "10", true),
                    part(2, "12", true),
                    part(3, "2", true),
                    part(4, "5", true),
                    part(5, null, false),
                    part(6, "99", true)));

    assertThat(report.partCount().netIncrease()).isEqualByComparingTo("5");
    assertThat(report.partCount().usedTransitionCount()).isEqualTo(2);
    assertThat(report.partCount().resetCount()).isEqualTo(1);
    assertThat(report.partCount().unavailableObservationCount()).isEqualTo(1);
    assertThat(report.partCount().associations().getFirst().relationship())
        .isEqualTo("TEMPORAL_OVERLAP_ONLY");
    assertThat(report.partCount().associations().getFirst().overlappingMachiningRunIds())
        .containsExactly("run-1");
  }

  private static ObservedProductionContext.ProgramObservation program(
      long sequence, String kind, String value, boolean available) {
    return new ObservedProductionContext.ProgramObservation(
        kind,
        available,
        value,
        sequence,
        START.plusSeconds(sequence),
        "program-" + sequence,
        "raw-" + sequence,
        "Mazak01-path_" + (kind.equals("MAIN") ? "1" : "2"));
  }

  private static ObservedProductionContext.ObservedMachiningRun run(
      String id, String program, long startSeconds, long endSeconds, String status) {
    return new ObservedProductionContext.ObservedMachiningRun(
        id, program, status, START.plusSeconds(startSeconds), START.plusSeconds(endSeconds));
  }

  private static ObservedProductionContext.PartCountObservation part(
      long sequence, String value, boolean available) {
    return new ObservedProductionContext.PartCountObservation(
        available,
        value == null ? null : new BigDecimal(value),
        sequence,
        START.plusSeconds(sequence * 10),
        "part-" + sequence,
        "raw-part-" + sequence,
        "Mazak01-path_6");
  }
}
