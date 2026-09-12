package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OperationalTwinSnapshot(
    String machineId,
    TwinVersion twinVersion,
    Instant projectedAt,
    ReplayCursor replayCursor,
    TwinConsistencyState consistencyState,
    List<String> missingFields,
    ConnectivityState connectivity,
    ExecutionState execution,
    HealthState health,
    FreshnessState freshness,
    Instant evaluatedAt,
    Duration age,
    long freshMaxAgeMillis,
    long laggingMaxAgeMillis,
    List<FieldProvenance> connectivityProvenance,
    List<FieldProvenance> executionProvenance,
    List<FieldProvenance> healthProvenance,
    TwinMetrics metrics,
    List<CurrentCondition> conditions,
    Optional<SpatialLayout> spatial) {

  public OperationalTwinSnapshot {
    Objects.requireNonNull(machineId, "machineId");
    Objects.requireNonNull(twinVersion, "twinVersion");
    Objects.requireNonNull(projectedAt, "projectedAt");
    Objects.requireNonNull(replayCursor, "replayCursor");
    Objects.requireNonNull(consistencyState, "consistencyState");
    missingFields = List.copyOf(missingFields);
    Objects.requireNonNull(connectivity, "connectivity");
    Objects.requireNonNull(execution, "execution");
    Objects.requireNonNull(health, "health");
    Objects.requireNonNull(freshness, "freshness");
    Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    Objects.requireNonNull(age, "age");
    if (freshMaxAgeMillis < 0 || laggingMaxAgeMillis < freshMaxAgeMillis) {
      throw new IllegalArgumentException("freshness thresholds must form a non-negative window");
    }
    connectivityProvenance = List.copyOf(connectivityProvenance);
    executionProvenance = List.copyOf(executionProvenance);
    healthProvenance = List.copyOf(healthProvenance);
    Objects.requireNonNull(metrics, "metrics");
    conditions = List.copyOf(conditions);
    Objects.requireNonNull(spatial, "spatial");
  }

  /**
   * Observed metric channels of one snapshot. Channels that expose more than one source DataItem
   * stay collections so the contract never elects a primary component on the source's behalf.
   */
  public record TwinMetrics(
      List<SpindleSpeed> spindleSpeeds,
      List<AxisPosition> axisPositions,
      List<ObservedSample> loads,
      List<ObservedSample> temperatures,
      Optional<ObservedSample> pathFeedrate,
      Optional<ObservedAngle> bAxisAngle,
      Optional<ObservedEvent> toolNumber,
      Optional<ObservedEvent> partCount,
      Optional<ObservedEvent> program,
      Optional<ObservedEvent> controllerMode,
      Optional<ObservedEvent> powerState) {

    public TwinMetrics {
      spindleSpeeds = List.copyOf(spindleSpeeds);
      axisPositions = List.copyOf(axisPositions);
      loads = List.copyOf(loads);
      temperatures = List.copyOf(temperatures);
      Objects.requireNonNull(pathFeedrate, "pathFeedrate");
      Objects.requireNonNull(bAxisAngle, "bAxisAngle");
      Objects.requireNonNull(toolNumber, "toolNumber");
      Objects.requireNonNull(partCount, "partCount");
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(controllerMode, "controllerMode");
      Objects.requireNonNull(powerState, "powerState");
    }

    public static TwinMetrics none() {
      return new TwinMetrics(
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }
  }

  public record ObservationMetadata(
      String componentId,
      Instant sourceObservedAt,
      Instant projectedAt,
      TwinVersion twinVersion,
      FieldProvenance provenance) {

    public ObservationMetadata {
      Objects.requireNonNull(componentId, "componentId");
      Objects.requireNonNull(sourceObservedAt, "sourceObservedAt");
      Objects.requireNonNull(projectedAt, "projectedAt");
      Objects.requireNonNull(twinVersion, "twinVersion");
      Objects.requireNonNull(provenance, "provenance");
    }
  }

  public record SpindleSpeed(
      ObservationAvailability availability,
      BigDecimal value,
      String unit,
      ObservationMetadata metadata) {}

  /** A numeric Sample channel whose component identity lives in its own observation metadata. */
  public record ObservedSample(
      ObservationAvailability availability,
      BigDecimal value,
      String unit,
      ObservationMetadata metadata) {}

  public record AxisPosition(
      String axis,
      ObservationAvailability availability,
      BigDecimal value,
      String unit,
      ObservationMetadata metadata) {

    public AxisPosition {
      Objects.requireNonNull(axis, "axis");
      if (!List.of("X", "Y", "Z").contains(axis)) {
        throw new IllegalArgumentException("axis must be X, Y, or Z");
      }
    }
  }

  public record ObservedAngle(
      ObservationAvailability availability,
      BigDecimal value,
      String unit,
      ObservationMetadata metadata) {}

  public record ObservedEvent(
      ObservationAvailability availability, String value, ObservationMetadata metadata) {}

  public record CurrentCondition(
      String conditionType,
      String level,
      String nativeCode,
      String nativeSeverity,
      String qualifier,
      String message,
      ObservationMetadata metadata) {}
}
