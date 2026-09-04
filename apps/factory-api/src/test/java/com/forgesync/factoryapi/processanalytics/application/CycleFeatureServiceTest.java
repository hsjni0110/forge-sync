package com.forgesync.factoryapi.processanalytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgesync.factoryapi.processanalytics.domain.BoundaryEvidence;
import com.forgesync.factoryapi.processanalytics.domain.CycleFeatureExtractor;
import com.forgesync.factoryapi.processanalytics.domain.CycleObservation;
import com.forgesync.factoryapi.processanalytics.domain.CycleSignal;
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

class CycleFeatureServiceTest {
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant START = Instant.parse("2016-10-05T09:00:00Z");
  private static final String SOURCE_PROCESSING_ID = "sha256:" + "a".repeat(64);

  @Test
  void processesCompletedRunsOnlyAndReusesAnIdenticalInput() {
    FakeStore store =
        new FakeStore(List.of(run(MachiningRunStatus.COMPLETED), run(MachiningRunStatus.ABORTED)));
    store.observations.add(metric(1, 0, "100"));
    CycleFeatureService service = service(store);

    CycleFeatureProcessingResult first = service.process(command());
    CycleFeatureProcessingResult repeated = service.process(command());

    assertThat(first.eligibleRunCount()).isEqualTo(1);
    assertThat(first.featureSets()).hasSize(1);
    assertThat(repeated.featureProcessingRunId()).isEqualTo(first.featureProcessingRunId());
    assertThat(repeated.isCreated()).isFalse();
    assertThat(store.preserveAttempts).isEqualTo(1);
  }

  @Test
  void lateRelevantObservationCreatesANewImmutableProcessingResult() {
    FakeStore store = new FakeStore(List.of(run(MachiningRunStatus.COMPLETED)));
    store.observations.add(metric(1, 0, "100"));
    CycleFeatureService service = service(store);
    var first = service.process(command());

    store.observations.add(metric(2, 5, "200"));
    var second = service.process(command());

    assertThat(second.featureProcessingRunId()).isNotEqualTo(first.featureProcessingRunId());
    assertThat(second.resultHash()).isNotEqualTo(first.resultHash());
    assertThat(store.results).hasSize(2);
  }

  @Test
  void preservesAStableEmptyResultWhenThereAreNoCompletedRuns() {
    FakeStore store = new FakeStore(List.of(run(MachiningRunStatus.INTERRUPTED)));
    var result = service(store).process(command());

    assertThat(result.eligibleRunCount()).isZero();
    assertThat(result.featureSets()).isEmpty();
    assertThat(result.inputObservationCount()).isZero();
    assertThat(result.featureProcessingRunId()).startsWith("sha256:");
  }

  private static CycleFeatureService service(FakeStore store) {
    return new CycleFeatureService(
        store,
        store,
        store,
        new CycleFeatureExtractor(),
        Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneOffset.UTC));
  }

  private static ProcessCycleFeaturesCommand command() {
    return new ProcessCycleFeaturesCommand("Mazak01", SOURCE_PROCESSING_ID, "1.0.0");
  }

  private static MachiningRun run(MachiningRunStatus status) {
    ObservationProvenance provenance = provenance();
    ObservationRange range =
        new ObservationRange(SESSION, 1, 10, START, START.plusSeconds(10), "first", "last");
    BoundaryEvidence evidence =
        new BoundaryEvidence("START", ProcessSignal.EXECUTION, "first", 1, START, provenance);
    return new MachiningRun(
        "sha256:" + status.ordinal() + "b".repeat(63),
        SOURCE_PROCESSING_ID,
        "1.0.0",
        "Mazak01",
        status,
        "155",
        START,
        START.plusSeconds(10),
        SegmentationConfidence.HIGH,
        List.of("EXECUTION_BOUNDARY"),
        range,
        evidence,
        evidence,
        List.of(),
        "sha256:" + "d".repeat(64));
  }

  private static CycleObservation metric(long sequence, long offset, String value) {
    return new CycleObservation(
        "Mazak01",
        SESSION,
        sequence,
        START.plusSeconds(offset),
        "metric-" + sequence,
        CycleSignal.SPINDLE_SPEED,
        "spindle",
        "spindle-speed",
        "REVOLUTION/MINUTE",
        true,
        null,
        new BigDecimal(value),
        provenance());
  }

  private static ObservationProvenance provenance() {
    return new ObservationProvenance(
        "REAL",
        "NIST",
        "nist-mazak01-20161005",
        "sha256:" + "e".repeat(64),
        "raw",
        "2.0.0",
        "spindle-speed");
  }

  private static final class FakeStore
      implements MachiningRunProcessingSource,
          CycleFeatureObservationHistory,
          CycleFeatureProjectionStore {
    private final MachiningRunProcessingResult source;
    private final List<CycleObservation> observations = new ArrayList<>();
    private final List<CycleFeatureProcessingResult> results = new ArrayList<>();
    private int preserveAttempts;

    FakeStore(List<MachiningRun> runs) {
      source =
          new MachiningRunProcessingResult(
              SOURCE_PROCESSING_ID,
              "Mazak01",
              SESSION,
              10,
              "1.0.0",
              "sha256:" + "f".repeat(64),
              10,
              "sha256:" + "1".repeat(64),
              Instant.parse("2026-09-04T01:00:00Z"),
              false,
              runs);
    }

    @Override
    public Optional<MachiningRunProcessingResult> findProcessingRun(String id) {
      return id.equals(SOURCE_PROCESSING_ID) ? Optional.of(source) : Optional.empty();
    }

    @Override
    public List<CycleObservation> readCycleObservations(
        String machineId,
        UUID replaySessionId,
        long throughReplaySequence,
        Instant beforeExclusive) {
      return List.copyOf(observations);
    }

    @Override
    public boolean preserve(CycleFeatureProcessingResult result) {
      preserveAttempts++;
      results.add(result);
      return true;
    }

    @Override
    public Optional<CycleFeatureProcessingResult> findCycleFeatureProcessingRun(String id) {
      return results.stream()
          .filter(result -> result.featureProcessingRunId().equals(id))
          .findFirst();
    }
  }
}
