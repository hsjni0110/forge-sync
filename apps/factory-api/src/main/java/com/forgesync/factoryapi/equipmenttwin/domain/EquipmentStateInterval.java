package com.forgesync.factoryapi.equipmenttwin.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * How long one state signal held one value. A null {@code value} is an interval the source reported
 * as unavailable, never a gap the policy filled in. A null {@code endedAt} is an interval no
 * observation has closed yet: it has no duration, because inventing one would invent an end.
 */
public record EquipmentStateInterval(
    StateSignal signal,
    String value,
    Instant startedAt,
    Instant endedAt,
    IntervalBoundaryEvidence startEvidence,
    IntervalBoundaryEvidence endEvidence) {

  public EquipmentStateInterval {
    Objects.requireNonNull(signal, "signal");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(startEvidence, "startEvidence");
    if (endedAt == null && endEvidence != null) {
      throw new IllegalArgumentException("An open interval cannot carry end evidence");
    }
    if (endedAt != null && endEvidence == null) {
      throw new IllegalArgumentException("A closed interval requires end evidence");
    }
    if (endedAt != null && endedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("endedAt must not precede startedAt");
    }
  }

  public boolean isOpen() {
    return endedAt == null;
  }

  public boolean isUnknown() {
    return value == null;
  }

  public Optional<Duration> duration() {
    return isOpen() ? Optional.empty() : Optional.of(Duration.between(startedAt, endedAt));
  }
}
