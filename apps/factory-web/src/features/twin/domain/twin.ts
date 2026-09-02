export type Freshness = "FRESH" | "LAGGING" | "STALE";

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
    connectivity: { value: string; provenance: unknown[] };
    execution: { value: string; provenance: unknown[] };
    health: { value: string; provenance: unknown[] };
    freshness: {
      value: Freshness;
      evaluatedAt: string;
      projectedAt: string;
      ageMillis: number;
      basis: "PROJECTED_AT";
    };
  };
  metrics: Record<string, unknown>;
  conditions: unknown[];
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
