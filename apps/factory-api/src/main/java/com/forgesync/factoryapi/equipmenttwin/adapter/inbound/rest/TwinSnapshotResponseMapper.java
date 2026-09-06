package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ConditionDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ConsistencyDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.DerivedStateDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.FieldProvenanceDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.FreshnessDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.MachineDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.MetricsDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ObservationMetadataDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ObservedAngleDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ObservedIntegerDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ObservedTextDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.SourceProvenanceDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.SpatialDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.SpindleSpeedDto;
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.TransformationProvenanceDto;
import com.forgesync.factoryapi.equipmenttwin.application.FieldProvenance;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservationMetadata;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedAngle;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedEvent;
import java.util.List;
import java.util.Map;

public final class TwinSnapshotResponseMapper {

  public TwinSnapshotResponse map(OperationalTwinSnapshot snapshot) {
    return new TwinSnapshotResponse(
        "1.4.0",
        new MachineDto(snapshot.machineId()),
        new ConsistencyDto(
            snapshot.consistencyState().name(),
            snapshot.twinVersion().value(),
            snapshot.projectedAt(),
            snapshot.missingFields()),
        new TwinSnapshotResponse.ReplayCursorDto(
            snapshot.replayCursor().replaySessionId(),
            snapshot.replayCursor().replaySequence(),
            snapshot.replayCursor().sourceObservedAt(),
            snapshot.replayCursor().replayPublishedAt(),
            snapshot.replayCursor().twinVersion().value()),
        new TwinSnapshotResponse.StateDto(
            new DerivedStateDto(
                snapshot.connectivity().name(), provenance(snapshot.connectivityProvenance())),
            new DerivedStateDto(
                snapshot.execution().name(), provenance(snapshot.executionProvenance())),
            new DerivedStateDto(snapshot.health().name(), provenance(snapshot.healthProvenance())),
            new FreshnessDto(
                snapshot.freshness().name(),
                snapshot.evaluatedAt(),
                snapshot.projectedAt(),
                snapshot.age().toMillis(),
                snapshot.freshMaxAgeMillis(),
                snapshot.laggingMaxAgeMillis(),
                "PROJECTED_AT")),
        new MetricsDto(
            snapshot.spindleSpeeds().stream()
                .map(
                    speed ->
                        new SpindleSpeedDto(
                            speed.availability().name(),
                            speed.value(),
                            speed.unit(),
                            metadata(speed.metadata()),
                            provenance(speed.metadata().provenance())))
                .toList(),
            snapshot.bAxisAngle().map(TwinSnapshotResponseMapper::bAxisAngle).orElse(null),
            snapshot.toolNumber().map(TwinSnapshotResponseMapper::toolNumber).orElse(null),
            snapshot.program().map(TwinSnapshotResponseMapper::program).orElse(null)),
        snapshot.conditions().stream()
            .map(
                condition ->
                    new ConditionDto(
                        condition.conditionType(),
                        condition.level(),
                        condition.nativeCode(),
                        condition.nativeSeverity(),
                        condition.qualifier(),
                        condition.message(),
                        metadata(condition.metadata()),
                        provenance(condition.metadata().provenance())))
            .toList(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        snapshot
            .spatial()
            .map(
                layout ->
                    new SpatialDto(
                        layout.assetId(),
                        layout.sceneNodeId(),
                        layout.position(),
                        layout.positionUnit(),
                        layout.rotation(),
                        layout.rotationUnit(),
                        layout.scale(),
                        layout.provenance()))
            .orElse(null));
  }

  private static ObservedIntegerDto toolNumber(ObservedEvent event) {
    return new ObservedIntegerDto(
        event.availability().name(),
        event.value() == null ? null : Long.valueOf(event.value()),
        metadata(event.metadata()),
        provenance(event.metadata().provenance()));
  }

  private static ObservedAngleDto bAxisAngle(ObservedAngle angle) {
    return new ObservedAngleDto(
        angle.availability().name(),
        angle.value(),
        angle.unit(),
        metadata(angle.metadata()),
        provenance(angle.metadata().provenance()));
  }

  private static ObservedTextDto program(ObservedEvent event) {
    return new ObservedTextDto(
        event.availability().name(),
        event.value(),
        metadata(event.metadata()),
        provenance(event.metadata().provenance()));
  }

  private static ObservationMetadataDto metadata(ObservationMetadata metadata) {
    return new ObservationMetadataDto(
        metadata.componentId(),
        metadata.sourceObservedAt(),
        metadata.projectedAt(),
        metadata.twinVersion().value());
  }

  private static List<FieldProvenanceDto> provenance(List<FieldProvenance> provenance) {
    return provenance.stream().map(TwinSnapshotResponseMapper::provenance).toList();
  }

  private static FieldProvenanceDto provenance(FieldProvenance provenance) {
    return new FieldProvenanceDto(
        new SourceProvenanceDto(
            provenance.sourceKind(),
            provenance.provider(),
            provenance.sourceSetId(),
            provenance.artifactId()),
        new TransformationProvenanceDto(
            provenance.rawRecordId(), provenance.mappingVersion(), provenance.sourceDataItemId()));
  }
}
