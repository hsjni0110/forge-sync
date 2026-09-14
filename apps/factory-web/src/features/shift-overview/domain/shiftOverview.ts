import type { DowntimeParetoReport } from "../../downtime/domain/downtimePareto";

export type ShiftState = "ACTIVE" | "READY" | "STOPPED" | "INTERRUPTED" | "UNKNOWN";

export interface ShiftInterval {
  state: ShiftState;
  startedAt: string;
  endedAt?: string;
}

export interface ShiftMarker {
  kind: "DOWNTIME" | "TOOL_CHANGE";
  sourceObservedAt: string;
  label: string;
  seekTo: string;
}

export interface ShiftOverview {
  machineId: string;
  replaySessionId: string;
  throughReplaySequence: number;
  observedFrom: string;
  observedTo: string;
  availabilityPercent?: number;
  cuttingPercent?: number;
  downtimeSeconds: number;
  totalMachiningCount: number;
  completedMachiningCount: number;
  intervalProcessingRunId: string;
  utilizationProcessingRunId: string;
  intervals: ShiftInterval[];
  markers: ShiftMarker[];
  pareto: DowntimeParetoReport;
}

export function timelinePercent(instant: string, observedFrom: string, observedTo: string): number {
  const start = Date.parse(observedFrom);
  const end = Date.parse(observedTo);
  const value = Date.parse(instant);
  if (![start, end, value].every(Number.isFinite) || end <= start) return 0;
  return Math.min(100, Math.max(0, ((value - start) / (end - start)) * 100));
}
