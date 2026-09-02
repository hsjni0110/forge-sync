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
  schemaVersion: "1.0.0";
  machine: { machineId: string };
  consistency: {
    status: "CONSISTENT" | "PARTIAL" | "STALE" | "DEGRADED";
    twinVersion: number;
    projectedAt: string;
    missingFields: string[];
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
      basis: "PROJECTED_AT";
    };
  };
  metrics: {
    spindleSpeeds: SpindleSpeed[];
    toolNumber?: ObservedValue<number>;
    program?: ObservedValue<string>;
  };
  conditions: CurrentCondition[];
  production: Record<string, never>;
  productionResult: Record<string, never>;
  alarms: unknown[];
  maintenance: Record<string, never>;
  intelligence: Record<string, never>;
  quality: Record<string, never>;
  spatial: Record<string, never>;
}

export interface TwinPatch {
  schemaVersion: "1.0.0";
  type: "TWIN_PATCH";
  machineId: string;
  baseVersion: number;
  targetVersion: number;
  projectedAt: string;
  snapshot: TwinSnapshot;
}
