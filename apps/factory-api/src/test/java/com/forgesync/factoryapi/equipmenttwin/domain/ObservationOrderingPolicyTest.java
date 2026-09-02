package com.forgesync.factoryapi.equipmenttwin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservationOrderingPolicyTest {

  private static final UUID REPLAY_SESSION =
      UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac");
  private static final UUID ANOTHER_REPLAY_SESSION =
      UUID.fromString("5dfd98c9-532e-43a7-ac9f-31b2db874920");
  private static final Instant SOURCE_TIME = Instant.parse("2016-10-05T09:01:37.891Z");

  private final ObservationOrderingPolicy policy = new ObservationOrderingPolicy();

  @Test
  void prefersReplaySequenceOverSourceTimeWithinSameSession() {
    ObservationOrder current = order(REPLAY_SESSION, 41, SOURCE_TIME.plusSeconds(10), "current");
    ObservationOrder candidate = order(REPLAY_SESSION, 42, SOURCE_TIME, "candidate");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.PROJECT);
  }

  @Test
  void usesSourceTimeWhenReplaySequenceIsEqual() {
    ObservationOrder current = order(REPLAY_SESSION, 42, SOURCE_TIME, "current");
    ObservationOrder candidate = order(REPLAY_SESSION, 42, SOURCE_TIME.plusMillis(1), "candidate");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.PROJECT);
  }

  @Test
  void usesSourceEventKeyAsFinalTieBreaker() {
    ObservationOrder current = order(REPLAY_SESSION, 42, SOURCE_TIME, "artifact#line=1");
    ObservationOrder candidate = order(REPLAY_SESSION, 42, SOURCE_TIME, "artifact#line=2");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.PROJECT);
  }

  @Test
  void ignoresReplaySequenceAcrossDifferentSessions() {
    ObservationOrder current = order(REPLAY_SESSION, 1, SOURCE_TIME, "current");
    ObservationOrder candidate =
        order(ANOTHER_REPLAY_SESSION, 999, SOURCE_TIME.minusSeconds(1), "candidate");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.KEEP_CURRENT);
  }

  @Test
  void keepsCurrentWhenOrderingValuesAreEqual() {
    ObservationOrder current = order(REPLAY_SESSION, 42, SOURCE_TIME, "same");
    ObservationOrder candidate = order(REPLAY_SESSION, 42, SOURCE_TIME, "same");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.KEEP_CURRENT);
  }

  @Test
  void keepsCurrentForLowerSequenceWithinSameSession() {
    ObservationOrder current = order(REPLAY_SESSION, 42, SOURCE_TIME, "current");
    ObservationOrder candidate =
        order(REPLAY_SESSION, 41, SOURCE_TIME.plusSeconds(10), "candidate");

    assertThat(policy.decide(candidate, current)).isEqualTo(ProjectionDecision.KEEP_CURRENT);
  }

  private static ObservationOrder order(
      UUID replaySession, long replaySequence, Instant sourceObservedAt, String sourceEventKey) {
    return new ObservationOrder(replaySession, replaySequence, sourceObservedAt, sourceEventKey);
  }
}
