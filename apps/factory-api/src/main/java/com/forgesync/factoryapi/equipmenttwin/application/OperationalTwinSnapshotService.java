package com.forgesync.factoryapi.equipmenttwin.application;

import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.AxisPosition;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.CurrentCondition;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservationMetadata;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedAngle;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedEvent;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedSample;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.SpindleSpeed;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.TwinMetrics;
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
  private static final String ANGLE = "ANGLE";
  private static final String POSITION = "POSITION";
  private static final String DEGREE = "DEGREE";
  private static final String MILLIMETER = "MILLIMETER";
  private static final java.util.Map<String, String> AXIS_DATA_ITEMS =
      java.util.Map.of("X", "Mazak01-X_1", "Y", "Mazak01-Y_1", "Z", "Mazak01-Z_1");
  private static final String LOAD = "LOAD";
  private static final String TEMPERATURE = "TEMPERATURE";
  private static final String PATH_FEEDRATE = "PATH_FEEDRATE";
  private static final String MILLIMETER_PER_SECOND = "MILLIMETER/SECOND";
  private static final String EXECUTION = "EXECUTION";
  private static final String TOOL_NUMBER = "TOOL_NUMBER";
  private static final String PART_COUNT = "PART_COUNT";
  private static final String PROGRAM = "PROGRAM";
  private static final String CONTROLLER_MODE = "CONTROLLER_MODE";
  private static final String POWER_STATE = "POWER_STATE";

  private final TwinProjectionReader projectionReader;
  private final FreshnessPolicy freshnessPolicy;
  private final Clock clock;
  private final SpatialLayoutProvider spatialLayoutProvider;

  public OperationalTwinSnapshotService(
      TwinProjectionReader projectionReader, FreshnessPolicy freshnessPolicy, Clock clock) {
    this(projectionReader, freshnessPolicy, clock, machineId -> Optional.empty());
  }

  public OperationalTwinSnapshotService(
      TwinProjectionReader projectionReader,
      FreshnessPolicy freshnessPolicy,
      Clock clock,
      SpatialLayoutProvider spatialLayoutProvider) {
    this.projectionReader = Objects.requireNonNull(projectionReader);
    this.freshnessPolicy = Objects.requireNonNull(freshnessPolicy);
    this.clock = Objects.requireNonNull(clock);
    this.spatialLayoutProvider = Objects.requireNonNull(spatialLayoutProvider);
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
    List<AxisPosition> axisPositions = axisPositions(observations);
    Optional<ObservedAngle> bAxisAngle = uniqueBaxisAngle(observations);
    Optional<ObservedEvent> toolNumber = uniqueEvent(observations, TOOL_NUMBER);
    Optional<ObservedEvent> program = uniqueEvent(observations, PROGRAM);
    List<String> missingFields =
        missingFields(projection, spindleSpeeds, axisPositions, bAxisAngle, toolNumber, program);
    TwinConsistencyState consistency = consistency(freshness, missingFields);

    return new OperationalTwinSnapshot(
        projection.machineId(),
        projection.twinVersion(),
        projection.projectedAt(),
        projection.replayCursor(),
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
        new TwinMetrics(
            spindleSpeeds,
            axisPositions,
            samples(observations, LOAD),
            samples(observations, TEMPERATURE),
            uniqueSample(observations, PATH_FEEDRATE, MILLIMETER_PER_SECOND),
            bAxisAngle,
            toolNumber,
            uniqueEvent(observations, PART_COUNT),
            program,
            uniqueEvent(observations, CONTROLLER_MODE),
            uniqueEvent(observations, POWER_STATE)),
        conditions(observations),
        spatialLayoutProvider.findByMachineId(projection.machineId()));
  }

  private static void requireConsistentVersion(LoadedTwinProjection projection) {
    if (!projection.twinVersion().equals(projection.equipmentStateVersion())) {
      throw new TwinSnapshotUnavailableException("Twin projection versions do not match");
    }
    if (!projection.twinVersion().equals(projection.replayCursor().twinVersion())) {
      throw new TwinSnapshotUnavailableException("Replay cursor and Twin versions do not match");
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

  private static List<ObservedSample> samples(
      List<ProjectedTwinObservation> observations, String metric) {
    return observations.stream()
        .filter(item -> item.kind() == StateObservationKind.SAMPLE)
        .filter(item -> item.semanticType().equals(metric))
        .sorted(observationOrder())
        .map(OperationalTwinSnapshotService::observedSample)
        .toList();
  }

  private static Optional<ObservedSample> uniqueSample(
      List<ProjectedTwinObservation> observations, String metric, String expectedUnit) {
    List<ProjectedTwinObservation> matching =
        observations.stream()
            .filter(item -> item.kind() == StateObservationKind.SAMPLE)
            .filter(item -> item.semanticType().equals(metric))
            .sorted(observationOrder())
            .toList();
    if (matching.size() != 1 || !expectedUnit.equals(matching.getFirst().unit())) {
      return Optional.empty();
    }
    return Optional.of(observedSample(matching.getFirst()));
  }

  private static ObservedSample observedSample(ProjectedTwinObservation observation) {
    return new ObservedSample(
        observation.availability(),
        observation.numericValue(),
        observation.unit(),
        metadata(observation));
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

  private static Optional<ObservedAngle> uniqueBaxisAngle(
      List<ProjectedTwinObservation> observations) {
    List<ProjectedTwinObservation> matching =
        observations.stream()
            .filter(item -> item.kind() == StateObservationKind.SAMPLE)
            .filter(item -> item.semanticType().equals(ANGLE))
            .sorted(observationOrder())
            .toList();
    if (matching.size() != 1 || !DEGREE.equals(matching.getFirst().unit())) {
      return Optional.empty();
    }
    ProjectedTwinObservation item = matching.getFirst();
    return Optional.of(
        new ObservedAngle(item.availability(), item.numericValue(), item.unit(), metadata(item)));
  }

  private static List<AxisPosition> axisPositions(List<ProjectedTwinObservation> observations) {
    return AXIS_DATA_ITEMS.entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .map(entry -> uniqueAxisPosition(observations, entry.getKey(), entry.getValue()))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<AxisPosition> uniqueAxisPosition(
      List<ProjectedTwinObservation> observations, String axis, String sourceDataItemId) {
    List<ProjectedTwinObservation> matching =
        observations.stream()
            .filter(item -> item.kind() == StateObservationKind.SAMPLE)
            .filter(item -> item.semanticType().equals(POSITION))
            .filter(item -> item.provenance().sourceDataItemId().equals(sourceDataItemId))
            .toList();
    if (matching.size() != 1 || !MILLIMETER.equals(matching.getFirst().unit())) {
      return Optional.empty();
    }
    ProjectedTwinObservation item = matching.getFirst();
    return Optional.of(
        new AxisPosition(
            axis, item.availability(), item.numericValue(), item.unit(), metadata(item)));
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
      List<AxisPosition> axisPositions,
      Optional<ObservedAngle> bAxisAngle,
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
    for (String axis : List.of("X", "Y", "Z")) {
      if (axisPositions.stream()
          .filter(item -> item.axis().equals(axis))
          .noneMatch(OperationalTwinSnapshotService::isAvailable)) {
        missing.add("metrics.axisPositions." + axis.toLowerCase(java.util.Locale.ROOT));
      }
    }
    if (projection.equipmentState().execution() == ExecutionState.UNKNOWN) {
      missing.add("state.execution");
    }
    if (projection.equipmentState().health() == HealthState.UNKNOWN) {
      missing.add("state.health");
    }
    if (bAxisAngle.filter(OperationalTwinSnapshotService::isAvailable).isEmpty()) {
      missing.add("metrics.bAxisAngle");
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

  private static boolean isAvailable(ObservedAngle angle) {
    return angle.availability() == ObservationAvailability.AVAILABLE && angle.value() != null;
  }

  private static boolean isAvailable(AxisPosition position) {
    return position.availability() == ObservationAvailability.AVAILABLE && position.value() != null;
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
