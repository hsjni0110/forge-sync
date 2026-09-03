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
    tool,
    operationProgress: undefined,
    alarmSeverity: undefined,
    stale: freshness === "STALE",
    selected: selectedMachineId === snapshot.machine.machineId,
  };
}
