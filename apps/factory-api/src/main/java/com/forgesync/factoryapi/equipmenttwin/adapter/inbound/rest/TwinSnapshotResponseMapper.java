package com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest;

import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.AxisPositionDto;
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
import com.forgesync.factoryapi.equipmenttwin.adapter.inbound.rest.TwinSnapshotResponse.ObservedSampleDto;
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
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.ObservedSample;
import com.forgesync.factoryapi.equipmenttwin.application.OperationalTwinSnapshot.TwinMetrics;
import java.util.List;
import java.util.Map;

public final class TwinSnapshotResponseMapper {

  public TwinSnapshotResponse map(OperationalTwinSnapshot snapshot) {
    return new TwinSnapshotResponse(
        "1.6.0",
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
        metrics(snapshot.metrics()),
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

  private static MetricsDto metrics(TwinMetrics metrics) {
    return new MetricsDto(
        metrics.spindleSpeeds().stream()
            .map(
                speed ->
                    new SpindleSpeedDto(
                        speed.availability().name(),
                        speed.value(),
                        speed.unit(),
                        metadata(speed.metadata()),
                        provenance(speed.metadata().provenance())))
            .toList(),
        metrics.axisPositions().stream()
            .map(
                position ->
                    new AxisPositionDto(
                        position.axis(),
                        position.availability().name(),
                        position.value(),
                        position.unit(),
                        metadata(position.metadata()),
                        provenance(position.metadata().provenance())))
            .toList(),
        metrics.loads().stream().map(TwinSnapshotResponseMapper::observedSample).toList(),
        metrics.temperatures().stream().map(TwinSnapshotResponseMapper::observedSample).toList(),
        metrics.pathFeedrate().map(TwinSnapshotResponseMapper::observedSample).orElse(null),
        metrics.bAxisAngle().map(TwinSnapshotResponseMapper::bAxisAngle).orElse(null),
        metrics.toolNumber().map(TwinSnapshotResponseMapper::observedInteger).orElse(null),
        metrics.partCount().map(TwinSnapshotResponseMapper::observedInteger).orElse(null),
        metrics.program().map(TwinSnapshotResponseMapper::observedText).orElse(null),
        metrics.controllerMode().map(TwinSnapshotResponseMapper::observedText).orElse(null),
        metrics.powerState().map(TwinSnapshotResponseMapper::observedText).orElse(null));
  }

  private static ObservedSampleDto observedSample(ObservedSample sample) {
    return new ObservedSampleDto(
        sample.availability().name(),
        sample.value(),
        sample.unit(),
        metadata(sample.metadata()),
        provenance(sample.metadata().provenance()));
  }

  private static ObservedIntegerDto observedInteger(ObservedEvent event) {
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

  private static ObservedTextDto observedText(ObservedEvent event) {
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
