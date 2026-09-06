import { effectiveConnectivity } from "../../twin/domain/freshness";
import type { Freshness, TwinSnapshot } from "../../twin/domain/twin";
import type { MachineVisualState } from "../domain/machineVisualState";

export function mapTwinToMachineVisualState({
  snapshot,
  freshness,
  selectedMachineId,
  visualSpindleSourceDataItemId,
}: {
  snapshot: TwinSnapshot;
  freshness: Freshness;
  selectedMachineId: string | undefined;
  visualSpindleSourceDataItemId: string;
}): MachineVisualState {
  const spindle = snapshot.metrics.spindleSpeeds.find(
    ({ provenance }) =>
      provenance.transformation.sourceDataItemId === visualSpindleSourceDataItemId,
  );
  const rpm =
    spindle?.availability === "AVAILABLE" && spindle.value !== undefined
      ? spindle.value
      : undefined;
  const toolNumber = snapshot.metrics.toolNumber;
  const bAxisAngle = snapshot.metrics.bAxisAngle;
  const hasBaxisAngle =
    bAxisAngle?.availability === "AVAILABLE" &&
    bAxisAngle.value !== undefined &&
    bAxisAngle.unit === "DEGREE";
  const tool =
    toolNumber?.availability === "AVAILABLE" && toolNumber.value !== undefined
      ? String(toolNumber.value)
      : undefined;

  return {
    machineId: snapshot.machine.machineId,
    twinVersion: snapshot.consistency.twinVersion,
    connectivity: effectiveConnectivity(snapshot, freshness),
    execution: snapshot.state.execution.value,
    health: snapshot.state.health.value,
    rpm,
    rpmSourceDataItemId: visualSpindleSourceDataItemId,
    bAxisAngleDegrees: hasBaxisAngle ? bAxisAngle.value : undefined,
    bAxisAngleUnit: hasBaxisAngle ? bAxisAngle.unit : undefined,
    bAxisAngleSourceDataItemId: hasBaxisAngle
      ? bAxisAngle.provenance.transformation.sourceDataItemId
      : undefined,
    bAxisAngleSourceObservedAt: hasBaxisAngle
      ? bAxisAngle.observation.sourceObservedAt
      : undefined,
    tool,
    operationProgress: undefined,
    alarmSeverity: undefined,
    stale: freshness === "STALE",
    selected: selectedMachineId === snapshot.machine.machineId,
    spatial: snapshot.spatial ? {
      assetId: snapshot.spatial.assetId,
      sceneNodeId: snapshot.spatial.sceneNodeId,
      position: snapshot.spatial.position,
      rotation: snapshot.spatial.rotation,
      scale: snapshot.spatial.scale,
      provenance: snapshot.spatial.provenance,
    } : undefined,
  };
}
