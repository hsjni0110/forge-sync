package com.forgesync.factoryapi.processanalytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachiningRunSegmentationPolicyTest {

  private static final String PROCESSING_RUN_ID = "sha256:" + "1".repeat(64);
  private static final UUID SESSION = UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private final MachiningRunSegmentationPolicy policy = new MachiningRunSegmentationPolicy();

  @Test
  void createsTheSameCompletedRunForTheSameOrderedObservations() {
    List<ProcessObservation> observations =
        List.of(
            execution(1, 0, "READY"),
            program(2, 1, "155"),
            execution(3, 2, "ACTIVE"),
            spindle(4, 3, 2000),
            execution(5, 4, "FEED_HOLD"),
            execution(6, 5, "ACTIVE"),
            execution(7, 6, "READY"));

    List<MachiningRun> first = policy.segment(PROCESSING_RUN_ID, "1.0.0", observations);
    List<MachiningRun> second = policy.segment(PROCESSING_RUN_ID, "1.0.0", observations.reversed());

    assertThat(first).isEqualTo(second).hasSize(1);
    assertThat(first.getFirst().status()).isEqualTo(MachiningRunStatus.COMPLETED);
    assertThat(first.getFirst().programName()).isEqualTo("155");
    assertThat(first.getFirst().confidence()).isEqualTo(SegmentationConfidence.HIGH);
    assertThat(first.getFirst().startEvidence().sourceEventKey()).isEqualTo("event-3");
    assertThat(first.getFirst().endEvidence().sourceEventKey()).isEqualTo("event-7");
    assertThat(first.getFirst().observationRange().firstReplaySequence()).isEqualTo(2);
    assertThat(first.getFirst().supportingEvidence())
        .extracting(BoundaryEvidence::role)
        .containsExactly("PROGRAM", "POSITIVE_SPINDLE", "EXECUTION_CONFIRMED");
  }

  @Test
  void keepsMissingProgramWithoutInventingAName() {
    MachiningRun run =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(
                    execution(1, 0, "READY"), execution(2, 1, "ACTIVE"), execution(3, 2, "READY")))
            .getFirst();

    assertThat(run.status()).isEqualTo(MachiningRunStatus.COMPLETED);
    assertThat(run.programName()).isNull();
    assertThat(run.confidence()).isEqualTo(SegmentationConfidence.MEDIUM);
    assertThat(run.confidenceReasons()).contains("PROGRAM_MISSING");
  }

  @Test
  void preservesUnknownStartAndOpenEndInsteadOfGuessingBoundaries() {
    MachiningRun middleStart =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(execution(1, 0, "ACTIVE"), execution(2, 1, "READY")))
            .getFirst();
    MachiningRun openEnd =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(execution(1, 0, "READY"), execution(2, 1, "ACTIVE")))
            .getFirst();

    assertThat(middleStart.status()).isEqualTo(MachiningRunStatus.UNKNOWN);
    assertThat(openEnd.status()).isEqualTo(MachiningRunStatus.INTERRUPTED);
    assertThat(openEnd.endedAt()).isNull();
    assertThat(openEnd.endEvidence()).isNull();
  }

  @Test
  void distinguishesStoppedFromTemporaryExecutionPauses() {
    MachiningRun run =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(
                    execution(1, 0, "READY"),
                    execution(2, 1, "ACTIVE"),
                    execution(3, 2, "INTERRUPTED"),
                    execution(4, 3, "ACTIVE"),
                    execution(5, 4, "STOPPED")))
            .getFirst();

    assertThat(run.status()).isEqualTo(MachiningRunStatus.ABORTED);
    assertThat(run.observationRange().lastReplaySequence()).isEqualTo(5);
  }

  @Test
  void interruptsAnOpenRunWhenExecutionBecomesUnavailable() {
    ProcessObservation unavailable =
        new ProcessObservation(
            "Mazak01",
            SESSION,
            3,
            Instant.parse("2016-10-05T09:00:03Z"),
            "event-3",
            ProcessSignal.EXECUTION,
            false,
            null,
            null,
            provenance(3, ProcessSignal.EXECUTION));

    MachiningRun run =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(execution(1, 0, "READY"), execution(2, 1, "ACTIVE"), unavailable))
            .getFirst();

    assertThat(run.status()).isEqualTo(MachiningRunStatus.INTERRUPTED);
    assertThat(run.endedAt()).isEqualTo(unavailable.sourceObservedAt());
  }

  @Test
  void usesThirtySecondZeroSpindleOnlyAsFallbackWithoutExecution() {
    MachiningRun run =
        policy
            .segment(
                PROCESSING_RUN_ID,
                "1.0.0",
                List.of(
                    spindle(1, 0, 1000), spindle(2, 1, 0), spindle(3, 20, 0), spindle(4, 32, 0)))
            .getFirst();

    assertThat(run.status()).isEqualTo(MachiningRunStatus.INTERRUPTED);
    assertThat(run.endEvidence().sourceEventKey()).isEqualTo("event-4");
  }

  private static ProcessObservation execution(long sequence, long seconds, String value) {
    return observation(sequence, seconds, ProcessSignal.EXECUTION, value, null);
  }

  private static ProcessObservation program(long sequence, long seconds, String value) {
    return observation(sequence, seconds, ProcessSignal.PROGRAM, value, null);
  }

  private static ProcessObservation spindle(long sequence, long seconds, int value) {
    return observation(
        sequence, seconds, ProcessSignal.SPINDLE_SPEED, null, BigDecimal.valueOf(value));
  }

  private static ProcessObservation observation(
      long sequence,
      long seconds,
      ProcessSignal signal,
      String textValue,
      BigDecimal numericValue) {
    return new ProcessObservation(
        "Mazak01",
        SESSION,
        sequence,
        Instant.parse("2016-10-05T09:00:00Z").plusSeconds(seconds),
        "event-" + sequence,
        signal,
        true,
        textValue,
        numericValue,
        provenance(sequence, signal));
  }

  private static ObservationProvenance provenance(long sequence, ProcessSignal signal) {
    return new ObservationProvenance(
        "REAL",
        "NIST",
        "nist-mazak01-20161005",
        "sha256:" + "a".repeat(64),
        "raw-" + sequence,
        "2.0.0",
        signal.name().toLowerCase());
  }
}
