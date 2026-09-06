package com.forgesync.factoryapi.toolchange.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolChangePolicyTest {
  private static final UUID SESSION = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private final ToolChangePolicy policy = new ToolChangePolicy();

  @Test
  void emitsOnlyDistinctAvailableTransitionsWithTheirObservationEvidence() {
    var changes =
        policy.detect(List.of(observation(1, 4L), observation(2, 4L), observation(3, 7L)));

    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().fromToolNumber()).isEqualTo(4);
    assertThat(changes.getFirst().toToolNumber()).isEqualTo(7);
    assertThat(changes.getFirst().replaySequence()).isEqualTo(3);
    assertThat(changes.getFirst().rawRecordId()).isEqualTo("raw-3");
  }

  @Test
  void treatsUnavailableAsAGapAndDoesNotInferATransitionAcrossIt() {
    var changes =
        policy.detect(List.of(observation(1, 4L), observation(2, null), observation(3, 7L)));

    assertThat(changes).isEmpty();
  }

  @Test
  void preservesObservedZeroWithoutCallingItUnmounted() {
    var changes = policy.detect(List.of(observation(1, 4L), observation(2, 0L)));

    assertThat(changes.getFirst().toToolNumber()).isZero();
  }

  private static ToolNumberObservation observation(long sequence, Long value) {
    return new ToolNumberObservation(
        "Mazak01",
        SESSION,
        sequence,
        Instant.parse("2016-10-05T09:00:0" + sequence + "Z"),
        value,
        "Mazak01-path_10",
        "nist-mazak01-20161005",
        "artifact",
        "raw-" + sequence,
        "2.1.0");
  }
}
