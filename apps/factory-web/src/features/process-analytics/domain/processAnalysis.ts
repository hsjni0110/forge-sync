import { BASELINE_MINIMUM_SAMPLE_COUNT } from "./processGlossary";

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
  distance?: number | null; scale?: number | null;
}
export interface FeatureBaselineSummary {
  feature: string; sampleCount: number; unavailableReason: string | null;
}
export interface Assessment {
  status: string; classification?: string | null; score?: number | null;
  primaryFeature?: string | null; supportingOutlierCount?: number;
  reasons: AssessmentReason[]; evidence: TraceEntry[]; featureBaselines?: FeatureBaselineSummary[];
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

export function limitingFeatureBaseline(featureBaselines: FeatureBaselineSummary[] = []): FeatureBaselineSummary | undefined {
  const pending = featureBaselines.filter((baseline) => baseline.unavailableReason === "MINIMUM_SAMPLE_COUNT_NOT_MET");
  if (pending.length === 0) return undefined;
  return pending.find((baseline) => baseline.feature === "durationSeconds") ??
    pending.reduce((best, current) => (current.sampleCount > best.sampleCount ? current : best));
}

export function sampleProgressLabel(assessment?: Pick<Assessment, "status" | "featureBaselines">): string | undefined {
  if (!assessment || assessment.status !== "INSUFFICIENT_DATA") return undefined;
  const limiting = limitingFeatureBaseline(assessment.featureBaselines);
  return limiting ? `${limiting.sampleCount}/${BASELINE_MINIMUM_SAMPLE_COUNT}` : undefined;
}

export function assessmentUnavailableExplanation(
  program: string | undefined,
  assessment: Pick<Assessment, "status" | "featureBaselines">,
): string | undefined {
  if (assessment.status === "AVAILABLE") return undefined;
  if (assessment.status === "UNAVAILABLE") {
    return program
      ? "이 가공 자체의 측정값이 부족해 비교 기준을 만들 수 없습니다."
      : "이 가공은 프로그램 정보가 확인되지 않아 비교 기준을 만들 수 없습니다.";
  }
  if (assessment.status === "PARTIAL") {
    return "일부 측정값은 비교했지만, 다른 일부는 같은 프로그램의 이전 가공이 아직 부족합니다.";
  }
  const limiting = limitingFeatureBaseline(assessment.featureBaselines);
  if (!limiting) return "같은 프로그램의 이전 가공이 아직 충분하지 않습니다.";
  const remaining = BASELINE_MINIMUM_SAMPLE_COUNT - limiting.sampleCount;
  const programLabel = program ? `프로그램 ${program}` : "이 프로그램";
  return `같은 ${programLabel}의 이전 가공이 아직 ${limiting.sampleCount}건입니다. `
    + `최소 ${BASELINE_MINIMUM_SAMPLE_COUNT}건이 쌓여야 비교할 수 있습니다 (${remaining}건 더 필요).`;
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
