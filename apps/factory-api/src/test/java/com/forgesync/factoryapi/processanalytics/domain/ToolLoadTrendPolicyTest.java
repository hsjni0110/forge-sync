package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolLoadTrendPolicyTest {
  private static final Instant START = Instant.parse("2016-10-05T10:00:00Z");

  @Test
  void calculatesChannelSeparatedRunMediansAgainstTheFirstThreePointBaseline() {
    List<ToolLoadTrendReport.MachiningRunInput> runs = runs("155", 6);
    List<ToolLoadTrendReport.LoadObservation> loads = new ArrayList<>();
    addPoint(loads, 0, "Mazak01-C", "Mazak01-C_2", 10, 9, 11);
    addPoint(loads, 1, "Mazak01-C", "Mazak01-C_2", 12, 11, 13);
    addPoint(loads, 2, "Mazak01-C", "Mazak01-C_2", 14, 13, 15);
    addPoint(loads, 3, "Mazak01-C", "Mazak01-C_2", 18, 17, 19);
    addPoint(loads, 4, "Mazak01-C", "Mazak01-C_2", 20, 19, 21);
    addPoint(loads, 5, "Mazak01-C", "Mazak01-C_2", 22, 21, 23);
    addPoint(loads, 0, "Mazak01-X", "Mazak01-X_3", 80, 79, 81);

    ToolLoadTrendReport report =
        policy().project("Mazak01", "session-1", 999, hash('a'), runs, toolNumbers(6, 4), loads);

    assertThat(report.provenance().origin()).isEqualTo("DERIVED");
    assertThat(report.groups()).hasSize(2);
    ToolLoadTrendReport.Group spindle =
        report.groups().stream()
            .filter(group -> group.sourceDataItemId().equals("Mazak01-C_2"))
            .findFirst()
            .orElseThrow();
    assertThat(spindle.programName()).isEqualTo("155");
    assertThat(spindle.toolNumber()).isEqualTo(4);
    assertThat(spindle.status()).isEqualTo("AVAILABLE");
    assertThat(spindle.baselineMedianLoad()).isEqualByComparingTo("12.000000");
    assertThat(spindle.latestDeviationPercent()).isEqualByComparingTo("83.333333");
    assertThat(spindle.candidatePointCount()).isEqualTo(6);
    assertThat(spindle.eligiblePointCount()).isEqualTo(6);
    assertThat(spindle.coverageRatio()).isEqualByComparingTo("1.000000");
    assertThat(spindle.points().get(3).medianLoad()).isEqualByComparingTo("18.000000");
    assertThat(spindle.points().get(3).firstEvidence().sourceDataItemId()).isEqualTo("Mazak01-C_2");

    ToolLoadTrendReport.Group axis =
        report.groups().stream()
            .filter(group -> group.sourceDataItemId().equals("Mazak01-X_3"))
            .findFirst()
            .orElseThrow();
    assertThat(axis.status()).isEqualTo("INSUFFICIENT_SAMPLES");
  }

  @Test
  void neverMixesProgramToolOrLoadChannelGroups() {
    List<ToolLoadTrendReport.MachiningRunInput> runs = new ArrayList<>();
    runs.addAll(runs("155", 5));
    runs.add(run("other", "200", 600));
    List<ToolLoadTrendReport.LoadObservation> loads = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      addPoint(loads, index, "Mazak01-C", "Mazak01-C_2", 10 + index, 10 + index, 10 + index);
    }
    addPointAt(loads, 610, "Mazak01-C", "Mazak01-C_2", 99, 98, 100);

    List<ToolLoadTrendReport.ToolNumberObservation> tools = new ArrayList<>(toolNumbers(5, 4));
    tools.add(new ToolLoadTrendReport.ToolNumberObservation(true, 8, 900, START.plusSeconds(605)));
    ToolLoadTrendReport report =
        policy().project("Mazak01", "session-1", 999, hash('a'), runs, tools, loads);

    assertThat(report.groups())
        .extracting(group -> group.programName() + ":" + group.toolNumber())
        .containsExactly("155:4", "200:8");
    assertThat(report.groups().getFirst().points()).hasSize(5);
    assertThat(report.groups().getLast().points()).hasSize(1);
  }

  @Test
  void withholdsTrendForTooFewPointsLowCoverageAndZeroBaseline() {
    List<ToolLoadTrendReport.MachiningRunInput> runs = runs("155", 10);
    List<ToolLoadTrendReport.LoadObservation> loads = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      addPoint(loads, index, "Mazak01-C", "Mazak01-C_2", 0, 0, 0);
    }
    for (int index = 5; index < 10; index++) {
      addSparsePoint(loads, index, "Mazak01-C", "Mazak01-C_2", 20);
    }

    ToolLoadTrendReport report =
        policy().project("Mazak01", "session-1", 999, hash('a'), runs, toolNumbers(10, 4), loads);

    ToolLoadTrendReport.Group group = report.groups().getFirst();
    assertThat(group.status()).isEqualTo("INSUFFICIENT_COVERAGE");
    assertThat(group.reasons()).contains("COVERAGE_BELOW_0_8", "ZERO_BASELINE");
    assertThat(group.coverageRatio()).isEqualByComparingTo("0.500000");
    assertThat(group.latestDeviationPercent()).isNull();
    assertThat(group.slopePercentPerPoint()).isNull();
  }

  @Test
  void rejectsSimulationOrMixedSourceIdentityInsteadOfCallingItObserved() {
    var simulated = load("Mazak01-C", "Mazak01-C_2", 10, 10);
    simulated =
        new ToolLoadTrendReport.LoadObservation(
            simulated.available(),
            simulated.value(),
            simulated.unit(),
            simulated.componentId(),
            simulated.replaySequence(),
            simulated.sourceObservedAt(),
            simulated.sourceEventKey(),
            simulated.rawRecordId(),
            simulated.sourceDataItemId(),
            simulated.mappingVersion(),
            "SIMULATED",
            simulated.provider(),
            simulated.sourceSetId());

    ToolLoadTrendReport.LoadObservation finalSimulated = simulated;
    assertThatThrownBy(
            () ->
                policy()
                    .project(
                        "Mazak01",
                        "session-1",
                        999,
                        hash('a'),
                        runs("155", 1),
                        toolNumbers(1, 4),
                        List.of(finalSimulated)))
        .isInstanceOf(UnsupportedProcessSourceException.class)
        .hasMessageContaining("REAL");
  }

  private static ToolLoadTrendPolicy policy() {
    return new ToolLoadTrendPolicy();
  }

  private static List<ToolLoadTrendReport.MachiningRunInput> runs(String program, int count) {
    List<ToolLoadTrendReport.MachiningRunInput> result = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      result.add(run("run-" + index, program, index * 100L));
    }
    return result;
  }

  private static ToolLoadTrendReport.MachiningRunInput run(
      String id, String program, long startSeconds) {
    return new ToolLoadTrendReport.MachiningRunInput(
        id,
        program,
        "COMPLETED",
        START.plusSeconds(startSeconds),
        START.plusSeconds(startSeconds + 90));
  }

  private static List<ToolLoadTrendReport.ToolNumberObservation> toolNumbers(
      int runCount, int toolNumber) {
    List<ToolLoadTrendReport.ToolNumberObservation> result = new ArrayList<>();
    for (int index = 0; index < runCount; index++) {
      result.add(
          new ToolLoadTrendReport.ToolNumberObservation(
              true, toolNumber, index * 100L + 1, START.plusSeconds(index * 100L + 1)));
    }
    return result;
  }

  private static void addPoint(
      List<ToolLoadTrendReport.LoadObservation> loads,
      int runIndex,
      String componentId,
      String dataItemId,
      int... values) {
    addPointAt(loads, runIndex * 100L + 10, componentId, dataItemId, values);
  }

  private static void addPointAt(
      List<ToolLoadTrendReport.LoadObservation> loads,
      long startSeconds,
      String componentId,
      String dataItemId,
      int... values) {
    for (int index = 0; index < values.length; index++) {
      long sequence = startSeconds + index;
      loads.add(load(componentId, dataItemId, sequence, values[index]));
    }
  }

  private static void addSparsePoint(
      List<ToolLoadTrendReport.LoadObservation> loads,
      int runIndex,
      String componentId,
      String dataItemId,
      int value) {
    addPoint(loads, runIndex, componentId, dataItemId, value);
  }

  private static ToolLoadTrendReport.LoadObservation load(
      String componentId, String dataItemId, long sequence, int value) {
    return new ToolLoadTrendReport.LoadObservation(
        true,
        BigDecimal.valueOf(value),
        "PERCENT",
        componentId,
        sequence,
        START.plusSeconds(sequence),
        "event-" + dataItemId + "-" + sequence,
        "raw-" + sequence,
        dataItemId,
        "2.3.0",
        "REAL",
        "NIST",
        "nist-mazak01-20161005");
  }

  private static String hash(char value) {
    return "sha256:" + String.valueOf(value).repeat(64);
  }
}
