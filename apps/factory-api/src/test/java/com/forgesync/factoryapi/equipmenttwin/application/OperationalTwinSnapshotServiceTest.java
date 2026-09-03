package com.forgesync.factoryapi.equipmenttwin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgesync.factoryapi.equipmenttwin.domain.ConnectivityState;
import com.forgesync.factoryapi.equipmenttwin.domain.EquipmentState;
import com.forgesync.factoryapi.equipmenttwin.domain.ExecutionState;
import com.forgesync.factoryapi.equipmenttwin.domain.FreshnessPolicy;
import com.forgesync.factoryapi.equipmenttwin.domain.HealthState;
import com.forgesync.factoryapi.equipmenttwin.domain.ObservationAvailability;
import com.forgesync.factoryapi.equipmenttwin.domain.StateObservationKind;
import com.forgesync.factoryapi.equipmenttwin.domain.TwinVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationalTwinSnapshotServiceTest {

  private static final Instant PROJECTED_AT = Instant.parse("2026-09-02T01:02:04Z");
  private static final FreshnessPolicy FRESHNESS_POLICY =
      new FreshnessPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10));

  @Test
  void keepsEverySpindleOrderedByComponentAndPreservesFieldProvenance() {
    LoadedTwinProjection projection =
        projection(
            List.of(
                sample("Mazak01-C2", "Mazak01-C2_2", "2000"),
                sample("Mazak01-C", "Mazak01-C_5", "6842"),
                event("EXECUTION", "ACTIVE", "Mazak01-path_13"),
                event("TOOL_NUMBER", "13", "Mazak01-path_10"),
                event("PROGRAM", "114", "Mazak01-path_1")));

    OperationalTwinSnapshot snapshot =
        service(projection, PROJECTED_AT.plusSeconds(1)).getSnapshot("Mazak01");

    assertThat(snapshot.consistencyState()).isEqualTo(TwinConsistencyState.CONSISTENT);
    assertThat(snapshot.spindleSpeeds())
        .extracting(item -> item.metadata().provenance().sourceDataItemId())
        .containsExactly("Mazak01-C_5", "Mazak01-C2_2");
    assertThat(snapshot.spindleSpeeds())
        .extracting(item -> item.value().intValue())
        .containsExactly(6842, 2000);
    assertThat(snapshot.freshMaxAgeMillis()).isEqualTo(2_000);
    assertThat(snapshot.laggingMaxAgeMillis()).isEqualTo(10_000);
  }

  @Test
  void marksUnavailableP0ValuesPartialWithoutInventingDefaults() {
    ProjectedTwinObservation unavailableTool =
        event("TOOL_NUMBER", null, "Mazak01-path_10", ObservationAvailability.UNAVAILABLE);
    LoadedTwinProjection projection =
        new LoadedTwinProjection(
            "Mazak01",
            new TwinVersion(2),
            PROJECTED_AT,
            new EquipmentState(
                ConnectivityState.UNKNOWN, ExecutionState.UNKNOWN, HealthState.UNKNOWN),
            new TwinVersion(2),
            List.of(unavailableTool));

    OperationalTwinSnapshot snapshot =
        service(projection, PROJECTED_AT.plusSeconds(2)).getSnapshot("Mazak01");

    assertThat(snapshot.consistencyState()).isEqualTo(TwinConsistencyState.PARTIAL);
    assertThat(snapshot.missingFields())
        .containsExactly(
            "state.connectivity",
            "metrics.spindleSpeeds",
            "state.execution",
            "state.health",
            "metrics.toolNumber",
            "metrics.program");
    assertThat(snapshot.toolNumber())
        .get()
        .extracting(OperationalTwinSnapshot.ObservedEvent::value)
        .isNull();
  }

  @Test
  void staleFreshnessTakesPriorityOverPartialData() {
    LoadedTwinProjection projection =
        new LoadedTwinProjection(
            "Mazak01",
            new TwinVersion(1),
            PROJECTED_AT,
            new EquipmentState(
                ConnectivityState.ONLINE, ExecutionState.UNKNOWN, HealthState.UNKNOWN),
            new TwinVersion(1),
            List.of());

    OperationalTwinSnapshot snapshot =
        service(projection, PROJECTED_AT.plusSeconds(11)).getSnapshot("Mazak01");

    assertThat(snapshot.consistencyState()).isEqualTo(TwinConsistencyState.STALE);
    assertThat(snapshot.connectivity()).isEqualTo(ConnectivityState.STALE);
  }

  @Test
  void refusesToAssembleDifferentProjectionVersions() {
    LoadedTwinProjection mismatched =
        new LoadedTwinProjection(
            "Mazak01",
            new TwinVersion(3),
            PROJECTED_AT,
            new EquipmentState(ConnectivityState.ONLINE, ExecutionState.ACTIVE, HealthState.NORMAL),
            new TwinVersion(2),
            List.of());

    assertThatThrownBy(() -> service(mismatched, PROJECTED_AT).getSnapshot("Mazak01"))
        .isInstanceOf(TwinSnapshotUnavailableException.class);
  }

  @Test
  void refusesToPublishSnapshotWhenClockPrecedesProjection() {
    LoadedTwinProjection projection = projection(List.of());

    assertThatThrownBy(
            () -> service(projection, PROJECTED_AT.minusMillis(1)).getSnapshot("Mazak01"))
        .isInstanceOf(TwinSnapshotUnavailableException.class);
  }

  @Test
  void reportsUnknownMachineAsNotFound() {
    OperationalTwinSnapshotService service =
        new OperationalTwinSnapshotService(
            machineId -> Optional.empty(),
            FRESHNESS_POLICY,
            Clock.fixed(PROJECTED_AT, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.getSnapshot("missing"))
        .isInstanceOf(MachineTwinNotFoundException.class);
  }

  private static OperationalTwinSnapshotService service(
      LoadedTwinProjection projection, Instant evaluatedAt) {
    return new OperationalTwinSnapshotService(
        machineId -> Optional.of(projection),
        FRESHNESS_POLICY,
        Clock.fixed(evaluatedAt, ZoneOffset.UTC));
  }

  private static LoadedTwinProjection projection(List<ProjectedTwinObservation> observations) {
    return new LoadedTwinProjection(
        "Mazak01",
        new TwinVersion(5),
        PROJECTED_AT,
        new EquipmentState(ConnectivityState.ONLINE, ExecutionState.ACTIVE, HealthState.NORMAL),
        new TwinVersion(5),
        observations);
  }

  private static ProjectedTwinObservation sample(
      String componentId, String sourceDataItemId, String value) {
    return observation(
        StateObservationKind.SAMPLE,
        "SPINDLE_SPEED",
        ObservationAvailability.AVAILABLE,
        new BigDecimal(value),
        null,
        null,
        "REVOLUTION/MINUTE",
        componentId,
        sourceDataItemId);
  }

  private static ProjectedTwinObservation event(
      String eventType, String value, String sourceDataItemId) {
    return event(eventType, value, sourceDataItemId, ObservationAvailability.AVAILABLE);
  }

  private static ProjectedTwinObservation event(
      String eventType,
      String value,
      String sourceDataItemId,
      ObservationAvailability availability) {
    Long integer = eventType.equals("TOOL_NUMBER") && value != null ? Long.valueOf(value) : null;
    String text = integer == null ? value : null;
    return observation(
        StateObservationKind.EVENT,
        eventType,
        availability,
        null,
        text,
        integer,
        null,
        "Mazak01-path",
        sourceDataItemId);
  }

  private static ProjectedTwinObservation observation(
      StateObservationKind kind,
      String semanticType,
      ObservationAvailability availability,
      BigDecimal numericValue,
      String textValue,
      Long integerValue,
      String unit,
      String componentId,
      String sourceDataItemId) {
    return new ProjectedTwinObservation(
        kind,
        semanticType,
        availability,
        numericValue,
        textValue,
        integerValue,
        unit,
        componentId,
        Instant.parse("2016-10-05T08:43:49Z"),
        PROJECTED_AT,
        new TwinVersion(1),
        new FieldProvenance(
            "REAL",
            "NIST",
            "nist-mazak01-20161005",
            "sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf",
            "raw#" + sourceDataItemId,
            "2.0.0",
            sourceDataItemId),
        null,
        null,
        null,
        null);
  }
}
