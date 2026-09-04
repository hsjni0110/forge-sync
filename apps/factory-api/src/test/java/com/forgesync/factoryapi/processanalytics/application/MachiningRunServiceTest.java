package com.forgesync.factoryapi.processanalytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.processanalytics.domain.MachiningRun;
import com.forgesync.factoryapi.processanalytics.domain.MachiningRunSegmentationPolicy;
import com.forgesync.factoryapi.processanalytics.domain.ObservationProvenance;
import com.forgesync.factoryapi.processanalytics.domain.ProcessFactSourcePolicy;
import com.forgesync.factoryapi.processanalytics.domain.ProcessObservation;
import com.forgesync.factoryapi.processanalytics.domain.ProcessSignal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachiningRunServiceTest {

  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final Instant CREATED_AT = Instant.parse("2026-09-04T01:02:03Z");

  @Test
  void preservesOneImmutableResultAndReturnsItForAnIdenticalRequest() {
    FakeStore store = new FakeStore(observations());
    MachiningRunService service = service(store);
    SegmentMachiningRunsCommand command =
        new SegmentMachiningRunsCommand("Mazak01", SESSION, 3, "1.0.0");

    MachiningRunProcessingResult first = service.segment(command);
    MachiningRunProcessingResult second = service.segment(command);

    assertThat(first.isCreated()).isTrue();
    assertThat(second.isCreated()).isFalse();
    assertThat(second.processingRunId()).isEqualTo(first.processingRunId());
    assertThat(second.resultHash()).isEqualTo(first.resultHash());
    assertThat(store.preserveAttempts).isEqualTo(1);
  }

  @Test
  void changesProcessingIdentityWhenLateInputIsIncluded() {
    FakeStore store = new FakeStore(observations());
    MachiningRunService service = service(store);
    MachiningRunProcessingResult first =
        service.segment(new SegmentMachiningRunsCommand("Mazak01", SESSION, 3, "1.0.0"));
    store.observations.add(execution(4, "STOPPED"));
    MachiningRunProcessingResult reprocessed =
        service.segment(new SegmentMachiningRunsCommand("Mazak01", SESSION, 4, "1.0.0"));

    assertThat(reprocessed.processingRunId()).isNotEqualTo(first.processingRunId());
    assertThat(store.results).hasSize(2);
  }

  @Test
  void rejectsARequestWithNoCanonicalHistory() {
    MachiningRunService service = service(new FakeStore(List.of()));

    assertThatThrownBy(
            () -> service.segment(new SegmentMachiningRunsCommand("Mazak01", SESSION, 3, "1.0.0")))
        .isInstanceOf(CanonicalObservationHistoryNotFoundException.class);
  }

  private static MachiningRunService service(FakeStore store) {
    return new MachiningRunService(
        store,
        store,
        new MachiningRunSegmentationPolicy(),
        new ProcessFactSourcePolicy(),
        Clock.fixed(CREATED_AT, ZoneOffset.UTC));
  }

  private static List<ProcessObservation> observations() {
    return new ArrayList<>(
        List.of(execution(1, "READY"), execution(2, "ACTIVE"), execution(3, "READY")));
  }

  private static ProcessObservation execution(long sequence, String value) {
    return new ProcessObservation(
        "Mazak01",
        SESSION,
        sequence,
        Instant.parse("2016-10-05T09:00:00Z").plusSeconds(sequence),
        "event-" + sequence,
        ProcessSignal.EXECUTION,
        true,
        value,
        null,
        new ObservationProvenance(
            "REAL",
            "NIST",
            "nist-mazak01-20161005",
            "sha256:" + "a".repeat(64),
            "raw-" + sequence,
            "2.0.0",
            "execution"));
  }

  private static final class FakeStore
      implements CanonicalObservationHistory, MachiningRunProjectionStore {
    private final List<ProcessObservation> observations;
    private final List<MachiningRunProcessingResult> results = new ArrayList<>();
    private int preserveAttempts;

    FakeStore(List<ProcessObservation> observations) {
      this.observations = new ArrayList<>(observations);
    }

    @Override
    public List<ProcessObservation> readRelevantObservations(
        String machineId, UUID replaySessionId, long throughReplaySequence) {
      return observations.stream()
          .filter(observation -> observation.replaySequence() <= throughReplaySequence)
          .toList();
    }

    @Override
    public boolean preserve(MachiningRunProcessingResult processingResult) {
      preserveAttempts++;
      results.add(processingResult);
      return true;
    }

    @Override
    public Optional<MachiningRunProcessingResult> findProcessingRun(String processingRunId) {
      return results.stream()
          .filter(result -> result.processingRunId().equals(processingRunId))
          .findFirst();
    }

    @Override
    public List<MachiningRun> findMachiningRuns(String machineId, String processingRunId) {
      return findProcessingRun(processingRunId)
          .map(MachiningRunProcessingResult::machiningRuns)
          .orElse(List.of());
    }
  }
}
