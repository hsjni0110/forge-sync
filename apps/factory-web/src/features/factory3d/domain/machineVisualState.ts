import type { FactoryAsset } from "./factoryAsset";

export interface MachineVisualState {
  machineId: string;
  twinVersion: number;
  connectivity: "UNKNOWN" | "ONLINE" | "STALE" | "OFFLINE";
  execution: "UNKNOWN" | "READY" | "ACTIVE" | "IDLE" | "HOLD" | "STOPPED";
  health: "UNKNOWN" | "NORMAL" | "WARNING" | "FAULT";
  rpm?: number;
  rpmSourceDataItemId: string;
  tool?: string;
  operationProgress?: number;
  alarmSeverity?: "WARNING" | "FAULT";
  stale: boolean;
  selected: boolean;
  isReplayAdvancing?: boolean;
}

export interface MachineSceneBinding {
  machineId: string;
  sceneNodeId: string;
  asset: FactoryAsset;
  position: readonly [number, number, number];
  rotation: readonly [number, number, number];
  scale: readonly [number, number, number];
  spatialProvenance: "SIMULATED_LAYOUT";
  visualSpindleSourceDataItemId: string;
}
