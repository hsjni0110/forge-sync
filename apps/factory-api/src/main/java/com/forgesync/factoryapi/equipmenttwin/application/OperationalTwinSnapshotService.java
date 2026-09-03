package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.CurrentCondition;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservationMetadata;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedEvent;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.SpindleSpeed;
import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessState;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservationKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OperationalTwinSnapshotService implements GetOperationalTwinSnapshot {

  private static final String SPINDLE_SPEED = "SPINDLE_SPEED";
  private static final String EXECUTION = "EXECUTION";
  private static final String TOOL_NUMBER = "TOOL_NUMBER";
  private static final String PROGRAM = "PROGRAM";

  private final TwinProjectionReader projectionReader;
  private final FreshnessPolicy freshnessPolicy;
  private final Clock clock;

  public OperationalTwinSnapshotService(
      TwinProjectionReader projectionReader, FreshnessPolicy freshnessPolicy, Clock clock) {
    this.projectionReader = Objects.requireNonNull(projectionReader);
    this.freshnessPolicy = Objects.requireNonNull(freshnessPolicy);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public OperationalTwinSnapshot getSnapshot(String machineId) {
    LoadedTwinProjection projection =
        projectionReader
            .findByMachineId(machineId)
            .orElseThrow(() -> new MachineTwinNotFoundException(machineId));
    requireConsistentVersion(projection);
    Instant evaluatedAt = clock.instant();
    FreshnessState freshness = classifyFreshness(projection.projectedAt(), evaluatedAt);

    List<ProjectedTwinObservation> observations = projection.observations();
    List<SpindleSpeed> spindleSpeeds = spindleSpeeds(observations);
    Optional<ObservedEvent> toolNumber = uniqueEvent(observations, TOOL_NUMBER);
    Optional<ObservedEvent> program = uniqueEvent(observations, PROGRAM);
    List<String> missingFields = missingFields(projection, spindleSpeeds, toolNumber, program);
    TwinConsistencyState consistency = consistency(freshness, missingFields);

    return new OperationalTwinSnapshot(
        projection.machineId(),
        projection.twinVersion(),
        projection.projectedAt(),
        consistency,
        missingFields,
        projection.equipmentState().effectiveConnectivity(freshness),
        projection.equipmentState().execution(),
        projection.equipmentState().health(),
        freshness,
        evaluatedAt,
        Duration.between(projection.projectedAt(), evaluatedAt),
        freshnessPolicy.freshMaxAgeMillis(),
        freshnessPolicy.laggingMaxAgeMillis(),
        provenanceOfAvailable(observations),
        provenanceOf(observations, StateObservationKind.EVENT, EXECUTION),
        provenanceOfKind(observations, StateObservationKind.CONDITION),
        spindleSpeeds,
        toolNumber,
        program,
        conditions(observations));
  }

  private static void requireConsistentVersion(LoadedTwinProjection projection) {
    if (!projection.twinVersion().equals(projection.equipmentStateVersion())) {
      throw new TwinSnapshotUnavailableException("Twin projection versions do not match");
    }
  }

  private FreshnessState classifyFreshness(Instant projectedAt, Instant evaluatedAt) {
    try {
      return freshnessPolicy.classify(projectedAt, evaluatedAt);
    } catch (IllegalArgumentException exception) {
      throw new TwinSnapshotUnavailableException("Twin freshness cannot be evaluated", exception);
    }
  }

  private static List<SpindleSpeed> spindleSpeeds(List<ProjectedTwinObservation> observations) {
    return observations.stream()
        .filter(item -> item.kind() == StateObservationKind.SAMPLE)
        .filter(item -> item.semanticType().equals(SPINDLE_SPEED))
        .sorted(observationOrder())
        .map(
            item ->
                new SpindleSpeed(
                    item.availability(), item.numericValue(), item.unit(), metadata(item)))
        .toList();
  }

  private static Optional<ObservedEvent> uniqueEvent(
      List<ProjectedTwinObservation> observations, String eventType) {
    List<ProjectedTwinObservation> matching =
        observations.stream()
            .filter(item -> item.kind() == StateObservationKind.EVENT)
            .filter(item -> item.semanticType().equals(eventType))
            .sorted(observationOrder())
            .toList();
    if (matching.size() != 1) {
      return Optional.empty();
    }
    ProjectedTwinObservation item = matching.getFirst();
    String value = item.integerValue() == null ? item.textValue() : item.integerValue().toString();
    return Optional.of(new ObservedEvent(item.availability(), value, metadata(item)));
  }

  private static List<CurrentCondition> conditions(List<ProjectedTwinObservation> observations) {
    return observations.stream()
        .filter(item -> item.kind() == StateObservationKind.CONDITION)
        .sorted(observationOrder())
        .map(
            item ->
                new CurrentCondition(
                    item.semanticType(),
                    item.textValue(),
                    item.nativeCode(),
                    item.nativeSeverity(),
                    item.qualifier(),
                    item.message(),
                    metadata(item)))
        .toList();
  }

  private static List<String> missingFields(
      LoadedTwinProjection projection,
      List<SpindleSpeed> spindleSpeeds,
      Optional<ObservedEvent> toolNumber,
      Optional<ObservedEvent> program) {
    List<String> missing = new ArrayList<>();
    if (projection.equipmentState().connectivity() == ConnectivityState.UNKNOWN) {
      missing.add("state.connectivity");
    }
    if (spindleSpeeds.stream()
        .noneMatch(item -> item.availability() == ObservationAvailability.AVAILABLE)) {
      missing.add("metrics.spindleSpeeds");
    }
    if (projection.equipmentState().execution() == ExecutionState.UNKNOWN) {
      missing.add("state.execution");
    }
    if (projection.equipmentState().health() == HealthState.UNKNOWN) {
      missing.add("state.health");
    }
    if (toolNumber.filter(OperationalTwinSnapshotService::isAvailable).isEmpty()) {
      missing.add("metrics.toolNumber");
    }
    if (program.filter(OperationalTwinSnapshotService::isAvailable).isEmpty()) {
      missing.add("metrics.program");
    }
    return List.copyOf(missing);
  }

  private static boolean isAvailable(ObservedEvent event) {
    return event.availability() == ObservationAvailability.AVAILABLE;
  }

  private static TwinConsistencyState consistency(
      FreshnessState freshness, List<String> missingFields) {
    if (freshness == FreshnessState.STALE) {
      return TwinConsistencyState.STALE;
    }
    return missingFields.isEmpty() ? TwinConsistencyState.CONSISTENT : TwinConsistencyState.PARTIAL;
  }

  private static List<FieldProvenance> provenanceOfAvailable(
      List<ProjectedTwinObservation> observations) {
    return observations.stream()
        .filter(item -> item.availability() == ObservationAvailability.AVAILABLE)
        .sorted(observationOrder())
        .map(ProjectedTwinObservation::provenance)
        .toList();
  }

  private static List<FieldProvenance> provenanceOf(
      List<ProjectedTwinObservation> observations, StateObservationKind kind, String semanticType) {
    return observations.stream()
        .filter(item -> item.kind() == kind && item.semanticType().equals(semanticType))
        .sorted(observationOrder())
        .map(ProjectedTwinObservation::provenance)
        .toList();
  }

  private static List<FieldProvenance> provenanceOfKind(
      List<ProjectedTwinObservation> observations, StateObservationKind kind) {
    return observations.stream()
        .filter(item -> item.kind() == kind)
        .sorted(observationOrder())
        .map(ProjectedTwinObservation::provenance)
        .toList();
  }

  private static Comparator<ProjectedTwinObservation> observationOrder() {
    return Comparator.comparing(ProjectedTwinObservation::componentId)
        .thenComparing(item -> item.provenance().sourceDataItemId());
  }

  private static ObservationMetadata metadata(ProjectedTwinObservation observation) {
    return new ObservationMetadata(
        observation.componentId(),
        observation.sourceObservedAt(),
        observation.projectedAt(),
        observation.twinVersion(),
        observation.provenance());
  }
}
