export interface ProcessCursor {
  replaySessionId: string;
  replaySequence: number;
  sourceObservedAt: string;
  replayPublishedAt: string;
  twinVersion: number;
}

export interface MachiningRun {
  id: string;
  status: "COMPLETED" | "INTERRUPTED" | "ABORTED" | "UNKNOWN";
  program?: string;
  startedAt: string;
  endedAt?: string;
  startSequence: number;
  endSequence?: number;
  confidence: string;
  reasons: string[];
  evidence: TraceEntry[];
  feature?: CycleFeature;
  assessment?: Assessment;
}

export interface RunAnalysis {
  processingId: string;
  featureProcessingId: string;
  assessmentProcessingId: string;
  runs: MachiningRun[];
}

export interface TraceEntry { label: string; value: string }
export interface MetricFeature {
  metric: string; component?: string; sourceDataItem?: string; unit?: string;
  status: string; mean?: number; maximum?: number; standardDeviation?: number;
  coverageRatio: number | null; evidence: TraceEntry[];
}
export interface CycleFeature {
  id: string; status: string; durationSeconds: number;
  cuttingSeconds: number | null; idleSeconds: number | null; coverageRatio: number | null;
  metrics: MetricFeature[]; evidence: TraceEntry[];
}
export interface AssessmentReason {
  feature: string; target: number; median: number; difference: number;
  percentage?: number | null; direction: string; sampleCount: number; code: string;
}
export interface Assessment {
  status: string; classification?: string | null; score?: number | null;
  reasons: AssessmentReason[]; evidence: TraceEntry[];
}

export function currentRun(runs: MachiningRun[], cursor: ProcessCursor) {
  return runs.find((run) => run.startSequence <= cursor.replaySequence &&
    (run.endSequence === undefined || cursor.replaySequence < run.endSequence));
}

const RUN_STATUS_LABEL: Record<string, string> = {
  COMPLETED: "가공 완료",
  INTERRUPTED: "가공 중단",
  ABORTED: "가공 중지",
  UNKNOWN: "상태 확인 불가",
};

export function runStatusLabel(run: MachiningRun): string {
  const base = RUN_STATUS_LABEL[run.status] ?? run.status;
  return run.endedAt === undefined ? `${base} · 종료 근거 미확정` : base;
}

export function hasMatchingWatermark(cursor: ProcessCursor, session: {
  replaySessionId: string; publicationCursor?: { replaySequence: number; sourceObservedAt: string; replayPublishedAt: string };
}): boolean {
  return session.replaySessionId === cursor.replaySessionId &&
    session.publicationCursor?.replaySequence === cursor.replaySequence &&
    sameInstant(session.publicationCursor.sourceObservedAt, cursor.sourceObservedAt) &&
    sameInstant(session.publicationCursor.replayPublishedAt, cursor.replayPublishedAt);
}

function sameInstant(left: string, right: string): boolean {
  const normalize = (value: string) => {
    const parts = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})$/.exec(value);
    if (!parts) return undefined;
    const secondsMillis = Date.parse(parts[1] + parts[3]);
    return Number.isFinite(secondsMillis) ? `${secondsMillis}:${(parts[2] ?? "").replace(/0+$/, "")}` : undefined;
  };
  const first = normalize(left);
  return first !== undefined && first === normalize(right);
}
