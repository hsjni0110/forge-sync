package com.forgesync.factoryapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IngestObservationTest {

  private static final Instant INGESTED_AT = Instant.parse("2026-09-02T01:02:03Z");
  private static final Instant PROJECTED_AT = Instant.parse("2026-09-02T01:02:04Z");

  @Test
  void storesObservationWithInjectedWallClockAndReturnsAcceptance() {
    AtomicReference<Instant> capturedIngestedAt = new AtomicReference<>();
    AtomicReference<Instant> capturedProjectedAt = new AtomicReference<>();
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(INGESTED_AT, PROJECTED_AT);
    IngestObservation useCase =
        new IngestObservation(
            (observation, ingestedAt, projectedAt) -> {
              capturedIngestedAt.set(ingestedAt);
              capturedProjectedAt.set(projectedAt);
              return IngestionResult.ACCEPTED;
            },
            clock);

    IngestionResult result = useCase.acceptObservation(observation());

    assertThat(result).isEqualTo(IngestionResult.ACCEPTED);
    assertThat(capturedIngestedAt).hasValue(INGESTED_AT);
    assertThat(capturedProjectedAt).hasValue(PROJECTED_AT);
  }

  @Test
  void preservesDuplicateResultFromAtomicPersistenceBoundary() {
    IngestObservation useCase =
        new IngestObservation(
            (observation, ingestedAt, projectedAt) -> IngestionResult.SKIPPED_DUPLICATE,
            Clock.fixed(INGESTED_AT, ZoneOffset.UTC));

    assertThat(useCase.acceptObservation(observation()))
        .isEqualTo(IngestionResult.SKIPPED_DUPLICATE);
  }

  @Test
  void propagatesPersistenceFailureWithoutReportingAcceptance() {
    IngestObservation useCase =
        new IngestObservation(
            (observation, ingestedAt, projectedAt) -> {
              throw new IllegalStateException("database unavailable");
            },
            Clock.fixed(INGESTED_AT, ZoneOffset.UTC));

    assertThatThrownBy(() -> useCase.acceptObservation(observation()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
  }

  private static ValidatedObservationMessage observation() {
    return new ValidatedObservationMessage(
        "{}",
        UUID.fromString("f83a8401-5893-4a3d-a4b8-d71f772c84c3"),
        "Mazak01",
        "controller",
        "EVENT",
        Instant.parse("2016-10-05T13:39:17.590347Z"),
        UUID.fromString("00d64db8-967e-41ba-9d09-fdd087710aac"),
        0,
        Instant.parse("2026-09-01T00:00:00Z"),
        "artifact#line=1",
        "sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf",
        "artifact#line=1",
        "2.0.0",
        "execution");
  }
}
