export type Freshness = "FRESH" | "LAGGING" | "STALE";
export type Availability = "AVAILABLE" | "UNAVAILABLE";

export interface SourceProvenance {
  kind: "REAL";
  provider: "NIST";
  sourceSetId: string;
  artifactId: string;
}

export interface TransformationProvenance {
  rawRecordId: string;
  mappingVersion: string;
  sourceDataItemId: string;
}

export interface FieldProvenance {
  source: SourceProvenance;
  transformation: TransformationProvenance;
}

export interface ObservationMetadata {
  componentId: string;
  sourceObservedAt: string;
  projectedAt: string;
  twinVersion: number;
}

export interface DerivedState<TValue extends string> {
  value: TValue;
  provenance: FieldProvenance[];
}

export interface ObservedValue<TValue> {
  availability: Availability;
  value?: TValue;
  observation: ObservationMetadata;
  provenance: FieldProvenance;
}

export interface SpindleSpeed extends ObservedValue<number> {
  unit?: "REVOLUTION/MINUTE";
}

export interface BAxisAngle extends ObservedValue<number> {
  unit?: "DEGREE";
}

export interface AxisPosition extends ObservedValue<number> {
  axis: "X" | "Y" | "Z";
  unit?: "MILLIMETER";
}

export interface ComponentLoad extends ObservedValue<number> {
  unit?: "PERCENT";
}

export interface ComponentTemperature extends ObservedValue<number> {
  unit?: "CELSIUS";
}

export interface PathFeedrate extends ObservedValue<number> {
  unit?: "MILLIMETER/SECOND";
}

export interface CurrentCondition {
  conditionType: string;
  level: "NORMAL" | "WARNING" | "FAULT" | "UNAVAILABLE";
  nativeCode?: string;
  nativeSeverity?: string;
  qualifier?: string;
  message?: string;
  observation: ObservationMetadata;
  provenance: FieldProvenance;
}

export interface TwinSnapshot {
  schemaVersion: "1.6.0";
  machine: { machineId: string };
  consistency: {
    status: "CONSISTENT" | "PARTIAL" | "STALE" | "DEGRADED";
    twinVersion: number;
    projectedAt: string;
    missingFields: string[];
  };
  replayCursor: {
    replaySessionId: string;
    replaySequence: number;
    sourceObservedAt: string;
    replayPublishedAt: string;
    twinVersion: number;
  };
  state: {
    connectivity: DerivedState<"UNKNOWN" | "ONLINE" | "STALE" | "OFFLINE">;
    execution: DerivedState<
      "UNKNOWN" | "READY" | "ACTIVE" | "IDLE" | "HOLD" | "STOPPED"
    >;
    health: DerivedState<"UNKNOWN" | "NORMAL" | "WARNING" | "FAULT">;
    freshness: {
      value: Freshness;
      evaluatedAt: string;
      projectedAt: string;
      ageMillis: number;
      freshMaxAgeMillis: number;
      laggingMaxAgeMillis: number;
      basis: "PROJECTED_AT";
    };
  };
  metrics: {
    spindleSpeeds: SpindleSpeed[];
    axisPositions: AxisPosition[];
    loads?: ComponentLoad[];
    temperatures?: ComponentTemperature[];
    pathFeedrate?: PathFeedrate;
    bAxisAngle?: BAxisAngle;
    toolNumber?: ObservedValue<number>;
    partCount?: ObservedValue<number>;
    program?: ObservedValue<string>;
    controllerMode?: ObservedValue<string>;
    powerState?: ObservedValue<string>;
  };
  conditions: CurrentCondition[];
  production: Record<string, never>;
  productionResult: Record<string, never>;
  alarms: unknown[];
  maintenance: Record<string, never>;
  intelligence: Record<string, never>;
  quality: Record<string, never>;
  spatial?: SpatialLayout;
}

export interface SpatialLayout {
  assetId: string;
  sceneNodeId: string;
  position: [number, number, number];
  positionUnit: "SCENE_UNIT";
  rotation: [number, number, number];
  rotationUnit: "RADIAN";
  scale: [number, number, number];
  provenance: "SIMULATED_LAYOUT";
}

export interface TwinPatch {
  schemaVersion: "1.6.0";
  type: "TWIN_PATCH";
  machineId: string;
  baseVersion: number;
  targetVersion: number;
  projectedAt: string;
  snapshot: TwinSnapshot;
}
