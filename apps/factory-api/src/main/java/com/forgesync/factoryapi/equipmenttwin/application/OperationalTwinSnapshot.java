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
    List<SpindleSpeed> spindleSpeeds,
    List<AxisPosition> axisPositions,
    Optional<ObservedAngle> bAxisAngle,
    Optional<ObservedEvent> toolNumber,
    Optional<ObservedEvent> program,
    List<CurrentCondition> conditions,
    Optional<SpatialLayout> spatial) {

  public OperationalTwinSnapshot(
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
      List<SpindleSpeed> spindleSpeeds,
      Optional<ObservedAngle> bAxisAngle,
      Optional<ObservedEvent> toolNumber,
      Optional<ObservedEvent> program,
      List<CurrentCondition> conditions) {
    this(
        machineId,
        twinVersion,
        projectedAt,
        replayCursor,
        consistencyState,
        missingFields,
        connectivity,
        execution,
        health,
        freshness,
        evaluatedAt,
        age,
        freshMaxAgeMillis,
        laggingMaxAgeMillis,
        connectivityProvenance,
        executionProvenance,
        healthProvenance,
        spindleSpeeds,
        List.of(),
        bAxisAngle,
        toolNumber,
        program,
        conditions,
        Optional.empty());
  }

  public OperationalTwinSnapshot(
      String machineId,
      TwinVersion twinVersion,
      Instant projectedAt,
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
      List<SpindleSpeed> spindleSpeeds,
      Optional<ObservedAngle> bAxisAngle,
      Optional<ObservedEvent> toolNumber,
      Optional<ObservedEvent> program,
      List<CurrentCondition> conditions) {
    this(
        machineId,
        twinVersion,
        projectedAt,
        new ReplayCursor(new java.util.UUID(0, 0), 0, projectedAt, projectedAt, twinVersion),
        consistencyState,
        missingFields,
        connectivity,
        execution,
        health,
        freshness,
        evaluatedAt,
        age,
        freshMaxAgeMillis,
        laggingMaxAgeMillis,
        connectivityProvenance,
        executionProvenance,
        healthProvenance,
        spindleSpeeds,
        List.of(),
        bAxisAngle,
        toolNumber,
        program,
        conditions,
        Optional.empty());
  }

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
    spindleSpeeds = List.copyOf(spindleSpeeds);
    axisPositions = List.copyOf(axisPositions);
    Objects.requireNonNull(bAxisAngle, "bAxisAngle");
    Objects.requireNonNull(toolNumber, "toolNumber");
    Objects.requireNonNull(program, "program");
    conditions = List.copyOf(conditions);
    Objects.requireNonNull(spatial, "spatial");
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
