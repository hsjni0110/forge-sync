export interface ToolChangeProvenance {
  source: { kind: "REAL"; provider: "NIST"; sourceSetId: string; artifactId: string };
  transformation: { rawRecordId: string; mappingVersion: string; sourceDataItemId: string };
}

export interface ToolChange {
  replaySequence: number;
  sourceObservedAt: string;
  fromToolNumber: number;
  toToolNumber: number;
  provenance: ToolChangeProvenance;
}

export interface ToolChangeTimeline {
  schemaVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  toolChanges: ToolChange[];
}
