import type { FactoryAsset } from "./factoryAsset";
import type { BAxisCoordinateMapping } from "./bAxisCoordinateMapping";

export interface MachineVisualState {
  machineId: string;
  twinVersion: number;
  connectivity: "UNKNOWN" | "ONLINE" | "STALE" | "OFFLINE";
  execution: "UNKNOWN" | "READY" | "ACTIVE" | "IDLE" | "HOLD" | "STOPPED";
  health: "UNKNOWN" | "NORMAL" | "WARNING" | "FAULT";
  rpm?: number;
  rpmSourceDataItemId: string;
  bAxisAngleDegrees?: number;
  bAxisAngleUnit?: "DEGREE";
  bAxisAngleSourceDataItemId?: string;
  bAxisAngleSourceObservedAt?: string;
  tool?: string;
  toolSourceDataItemId?: string;
  toolSourceObservedAt?: string;
  operationProgress?: number;
  alarmSeverity?: "WARNING" | "FAULT";
  stale: boolean;
  selected: boolean;
  isReplayAdvancing?: boolean;
  spatial?: MachineSpatialLayout;
}

export interface MachineSpatialLayout {
  assetId: string;
  sceneNodeId: string;
  position: readonly [number, number, number];
  rotation: readonly [number, number, number];
  scale: readonly [number, number, number];
  provenance: "SIMULATED_LAYOUT";
}

export interface MachineSceneBinding {
  machineId: string;
  sceneNodeId: string;
  asset: FactoryAsset;
  position: readonly [number, number, number];
  rotation: readonly [number, number, number];
  scale: readonly [number, number, number];
  spatialProvenance: "SIMULATED_LAYOUT";
  spatialAvailability: "TWIN" | "FALLBACK";
  visualSpindleSourceDataItemId: string;
  bAxisCoordinateMapping?: BAxisCoordinateMapping;
}
