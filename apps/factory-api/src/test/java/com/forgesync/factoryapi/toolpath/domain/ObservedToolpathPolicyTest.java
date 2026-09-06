package com.forgesync.factoryapi.toolpath.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObservedToolpathPolicyTest {
  private final ObservedToolpathPolicy policy = new ObservedToolpathPolicy(100, 3);

  @Test
  void buildsOnlyCompleteObservedPointsWithinTheSelectedRunAndDecimatesByTime() {
    var observations =
        List.of(
            observation("X", 1, 10, 0),
            observation("Y", 2, 20, 0),
            observation("Z", 3, 30, 0),
            observation("X", 10, 11, 0),
            observation("Y", 11, 21, 50),
            observation("Z", 12, 31, 100),
            observation("X", 13, 12, 150),
            observation("Y", 14, 22, 200));

    var result = policy.build(observations, 10, 14);

    assertThat(result.availability()).isEqualTo(ToolpathAvailability.AVAILABLE);
    assertThat(result.points())
        .extracting(ToolpathPoint::replaySequence)
        .containsExactly(10L, 12L, 14L);
    assertThat(result.points().getFirst().coordinatesMillimeters())
        .containsExactly(11.0, 20.0, 30.0);
    assertThat(result.points().getLast().coordinatesMillimeters())
        .containsExactly(12.0, 22.0, 31.0);
    assertThat(result.observedEnvelope().minimumMillimeters()).containsExactly(11.0, 20.0, 30.0);
    assertThat(result.observedEnvelope().maximumMillimeters()).containsExactly(12.0, 22.0, 31.0);
  }

  @Test
  void reportsUnavailableWhenOneAxisWasNeverObservedAndKeepsOnlyTheNewestBoundedPoints() {
    var unavailable =
        policy.build(List.of(observation("X", 1, 10, 0), observation("Y", 2, 20, 0)), 1, 2);
    assertThat(unavailable.availability()).isEqualTo(ToolpathAvailability.UNAVAILABLE);
    assertThat(unavailable.reason()).contains("Z");

    var gap =
        policy.build(
            List.of(
                observation("X", 1, 0, 0),
                observation("Y", 2, 0, 0),
                observation("Z", 3, 0, 0),
                observation("X", 4, 1, 100),
                unavailable("Y", 5, 150),
                observation("Y", 6, 2, 200)),
            3,
            6);
    assertThat(gap.availability()).isEqualTo(ToolpathAvailability.UNAVAILABLE);
    assertThat(gap.points()).isEmpty();

    var bounded =
        policy.build(
            List.of(
                observation("X", 1, 0, 0),
                observation("Y", 2, 0, 0),
                observation("Z", 3, 0, 0),
                observation("X", 4, 1, 100),
                observation("X", 5, 2, 200),
                observation("X", 6, 3, 300)),
            3,
            6);
    assertThat(bounded.points())
        .extracting(ToolpathPoint::replaySequence)
        .containsExactly(4L, 5L, 6L);
  }

  private static AxisPositionObservation observation(
      String axis, long sequence, double value, long offsetMillis) {
    return new AxisPositionObservation(
        axis,
        sequence,
        Instant.parse("2016-10-05T09:00:00Z").plusMillis(offsetMillis),
        "AVAILABLE",
        value,
        "MILLIMETER",
        "Mazak01-" + axis + "_1",
        "nist-mazak01-20161005",
        "sha256:artifact",
        "sha256:artifact#line=" + sequence,
        "2.1.0");
  }

  private static AxisPositionObservation unavailable(
      String axis, long sequence, long offsetMillis) {
    return new AxisPositionObservation(
        axis,
        sequence,
        Instant.parse("2016-10-05T09:00:00Z").plusMillis(offsetMillis),
        "UNAVAILABLE",
        null,
        null,
        "Mazak01-" + axis + "_1",
        "nist-mazak01-20161005",
        "sha256:artifact",
        "sha256:artifact#line=" + sequence,
        "2.1.0");
  }
}
