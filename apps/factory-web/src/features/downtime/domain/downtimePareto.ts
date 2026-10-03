export type DowntimeState = "STOPPED" | "INTERRUPTED" | "UNKNOWN";
export type DowntimeEvidenceKind =
  | "ESTOP_OVERLAP"
  | "MODE_CHANGE"
  | "CONDITION_OBSERVATION";

export interface DowntimeEvidence {
  kind: DowntimeEvidenceKind;
  signal: "EMERGENCY_STOP" | "CONTROLLER_MODE" | "CONDITION";
  value: string;
  sourceObservedAt: string;
  replaySequence: number;
  sourceEventKey: string;
  componentId?: string;
  conditionType?: string;
  level?: "WARNING" | "FAULT";
  nativeCode?: string;
  message?: string;
}

export interface DowntimeParetoEntry {
  rank: number;
  state: DowntimeState;
  startedAt: string;
  endedAt: string;
  durationSeconds: number;
  ratioPercent: number;
  cumulativeRatioPercent: number;
  reasonClassification: "CONCURRENT_EVIDENCE" | "UNCONFIRMED_REASON";
  evidence: DowntimeEvidence[];
}

export interface DowntimeParetoReport {
  schemaVersion: "1.0.0";
  processingRunId: string;
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  totalDowntimeSeconds: number;
  entries: DowntimeParetoEntry[];
}

/** The Pareto ranks every non-operating interval; callers name which states they mean. */
export function secondsInStates(
  entries: Pick<DowntimeParetoEntry, "state" | "durationSeconds">[],
  states: DowntimeState[],
): number {
  return entries.filter((entry) => states.includes(entry.state))
    .reduce((total, entry) => total + entry.durationSeconds, 0);
}
