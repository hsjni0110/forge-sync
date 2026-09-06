package com.forgesync.factoryapi.processanalytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.processanalytics.domain.AnomalyAssessmentPolicy;
import com.forgesync.factoryapi.processanalytics.domain.AssessmentDataStatus;
import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.CycleBaselinePolicy;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeature;
import com.forgesync.factoryapi.processanalytics.domain.FeatureAvailability;
import com.forgesync.factoryapi.processanalytics.domain.FeatureCoverage;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunStatus;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ObservationRange;
import com.forgesync.factoryapi.processanalytics.domain.ProcessSignal;
import com.forgesync.factoryapi.processanalytics.domain.SegmentationConfidence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalyAssessmentServiceTest {
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final UUID SESSION = UUID.fromString("13a62600-71ac-446c-a5e8-ef36c70257ef");

  @Test
  void evaluatesEveryFeatureSetSequentiallyAndReusesIdenticalInput() {
    List<CycleFeatureSet> sets = new ArrayList<>();
    List<MachiningRun> runs = new ArrayList<>();
    for (int index = 1; index <= 6; index++) {
      sets.add(featureSet("set-" + index, "run-" + index, index, Integer.toString(index + 7)));
      runs.add(run("run-" + index, index, "P|1"));
    }
    sets.add(featureSet("same-time", "run-same", 6, "100"));
    runs.add(run("run-same", 6, "P|1"));
    sets.add(featureSet("other-program", "run-other", 2, "100"));
    runs.add(run("run-other", 2, "P:2"));
    var source = cycleResult(sets);
    var store = new FakeStore();
    var service = service(source, machiningResult(runs), store);

    var first = service.process(command());
    var repeated = service.process(command());

    assertThat(first.assessments()).hasSize(8);
    var sixth =
        first.assessments().stream()
            .filter(value -> value.targetFeatureSetId().equals("set-6"))
            .findFirst()
            .orElseThrow();
    assertThat(sixth.baseline().candidateFeatureSetIds())
        .containsExactly("set-1", "set-2", "set-3", "set-4", "set-5");
    assertThat(sixth.baseline().candidateFeatureSetIds())
        .doesNotContain("same-time", "other-program");
    assertThat(repeated.assessmentProcessingRunId()).isEqualTo(first.assessmentProcessingRunId());
    assertThat(repeated.isCreated()).isFalse();
    assertThat(store.results).hasSize(1);
  }

  @Test
  void doesNotGuessWhenProgramIsMissing() {
    var set = featureSet("missing", "missing-run", 1, "10");
    var service =
        service(
            cycleResult(List.of(set)),
            machiningResult(List.of(run("missing-run", 1, null))),
            new FakeStore());

    var assessment = service.process(command()).assessments().getFirst();

    assertThat(assessment.dataStatus()).isEqualTo(AssessmentDataStatus.UNAVAILABLE);
    assertThat(assessment.score()).isNull();
  }

  private static AnomalyAssessmentService service(
      CycleFeatureProcessingResult cycle, MachiningRunProcessingResult machining, FakeStore store) {
    return new AnomalyAssessmentService(
        id -> Optional.of(cycle),
        id -> Optional.of(machining),
        store,
        new CycleBaselinePolicy(),
        new AnomalyAssessmentPolicy(),
        Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneOffset.UTC));
  }

  private static ProcessAnomalyAssessmentsCommand command() {
    return new ProcessAnomalyAssessmentsCommand("Mazak01", "cycle|source", "1.0.0", "2.0.0");
  }

  private static CycleFeatureProcessingResult cycleResult(List<CycleFeatureSet> sets) {
    return new CycleFeatureProcessingResult(
        "cycle|source",
        "machining:source",
        "Mazak01",
        "1.0.0",
        "input",
        0,
        sets.size(),
        "cycle-result",
        START,
        false,
        sets);
  }

  private static MachiningRunProcessingResult machiningResult(List<MachiningRun> runs) {
    return new MachiningRunProcessingResult(
        "machining:source",
        "Mazak01",
        SESSION,
        100,
        "1.0.0",
        "input",
        0,
        "machining-result",
        START,
        false,
        runs);
  }

  private static CycleFeatureSet featureSet(
      String setId, String runId, long offset, String duration) {
    Instant startedAt = START.plusSeconds(offset);
    BigDecimal value = new BigDecimal(duration);
    return new CycleFeatureSet(
        setId,
        new CycleFeature(
            "1.0.0",
            runId,
            startedAt,
            startedAt.plusSeconds(value.longValue()),
            FeatureAvailability.AVAILABLE,
            value,
            value,
            BigDecimal.ZERO,
            new FeatureCoverage(value, value, BigDecimal.ONE),
            List.of(),
            List.of(),
            null,
            List.of(),
            "result-" + setId));
  }

  private static MachiningRun run(String id, long offset, String program) {
    Instant startedAt = START.plusSeconds(offset);
    var provenance =
        new ObservationProvenance(
            "REAL", "NIST", "source", "artifact", "raw", "1.0.0", "execution");
    var range = new ObservationRange(SESSION, offset, offset, startedAt, startedAt, id, id);
    var evidence =
        new BoundaryEvidence("START", ProcessSignal.EXECUTION, id, offset, startedAt, provenance);
    return new MachiningRun(
        id,
        "machining:source",
        "1.0.0",
        "Mazak01",
        MachiningRunStatus.COMPLETED,
        program,
        startedAt,
        startedAt.plusSeconds(1),
        SegmentationConfidence.HIGH,
        List.of(),
        range,
        evidence,
        evidence,
        List.of(),
        "result-" + id);
  }

  private static final class FakeStore implements AnomalyAssessmentProjectionStore {
    private final List<AnomalyAssessmentProcessingResult> results = new ArrayList<>();

    @Override
    public boolean preserve(AnomalyAssessmentProcessingResult result) {
      results.add(result);
      return true;
    }

    @Override
    public Optional<AnomalyAssessmentProcessingResult> findAnomalyAssessmentProcessingRun(
        String id) {
      return results.stream()
          .filter(value -> value.assessmentProcessingRunId().equals(id))
          .findFirst();
    }
  }
}
