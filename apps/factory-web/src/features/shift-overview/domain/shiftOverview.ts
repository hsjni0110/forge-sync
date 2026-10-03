import type { DowntimeParetoReport } from "../../downtime/domain/downtimePareto";

export type ShiftState = "ACTIVE" | "READY" | "STOPPED" | "INTERRUPTED" | "UNKNOWN";

export interface ShiftInterval {
  state: ShiftState;
  startedAt: string;
  endedAt?: string;
}

export interface ShiftMarker {
  kind: "TOOL_CHANGE";
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
  /** ACTIVE time from the same utilization projection as availabilityPercent. */
  activeSeconds?: number;
  /** STOPPED and INTERRUPTED intervals; the observed but non-operating time we can name. */
  stoppedSeconds: number;
  /** Intervals opened by UNAVAILABLE: not operating as far as we know, but not observed as stopped. */
  unknownSeconds: number;
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

export interface TimelineTick { label: string; percent: number }

const TICK_STEPS_MINUTES = [5, 10, 15, 30, 60, 120, 180];
const MAX_TICKS = 8;

/** Round UTC marks, as fine as the view allows without crowding the axis. */
export function timelineTicks(from: string, to: string): TimelineTick[] {
  const start = Date.parse(from);
  const end = Date.parse(to);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return [];
  const stepMs = 60_000 * (TICK_STEPS_MINUTES.find((minutes) =>
    (end - start) / (minutes * 60_000) <= MAX_TICKS) ?? TICK_STEPS_MINUTES.at(-1)!);
  const ticks: TimelineTick[] = [];
  for (let at = Math.ceil(start / stepMs) * stepMs; at <= end; at += stepMs) {
    ticks.push({
      label: new Date(at).toISOString().slice(11, 16),
      percent: ((at - start) / (end - start)) * 100,
    });
  }
  return ticks;
}
