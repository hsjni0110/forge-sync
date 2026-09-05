import type { MachiningRun, TraceEntry } from "../domain/processAnalysis";
import { assessmentUnavailableExplanation, runStatusLabel } from "../domain/processAnalysis";
import { GLOSSARY } from "../domain/processGlossary";
import { HelpTip } from "./HelpTip";

export function RunDetail({ run }: { run: MachiningRun }) {
  return <article className="run-detail" aria-label="선택한 가공 상세">
    <header className="run-detail-header">
      <div>
        <h3>선택한 가공 · {run.program ?? "프로그램 미확인"}<HelpTip text={GLOSSARY.program} /></h3>
        <p className="run-detail-time">
          <span><strong>시작</strong> {formatUtc(run.startedAt)}</span>
          <span><strong>종료</strong> {run.endedAt ? formatUtc(run.endedAt) : "근거 미확정"}</span>
        </p>
      </div>
      <div className="run-detail-badges">
        <span className="status-badge">{runStatusLabel(run)}</span>
        <span className="provenance-tag">DERIVED</span><HelpTip text={GLOSSARY.derived} />
      </div>
    </header>
    <p className="section-note">신뢰도 {confidenceLabel(run.confidence)}<HelpTip text={GLOSSARY.confidence} /> · {run.reasons.map(reasonLabel).join(" · ")}</p>
    <TraceDetails title="가공의 관측 근거" helpText={GLOSSARY.observedEvidence} entries={run.evidence} observed />
    <section className="run-detail-block" aria-label="PROCESS · 공정 특징">
      <h3>PROCESS · 공정 특징<HelpTip text={GLOSSARY.cyclePurpose} /></h3>
      {!run.feature ? <p className="empty-state">완료된 가공만 분석 가능</p> : <>
        <p className="run-detail-badges">
          <span className="status-badge">{dataStatusLabel(run.feature.status)}</span>
          <span className="provenance-tag">DERIVED</span>
        </p>
        <dl className="definition-list">
          <div><dt>가공 구간<HelpTip text={GLOSSARY.runDuration} /></dt><dd>{run.feature.durationSeconds} 초</dd></div>
          <div><dt>절삭 시간<HelpTip text={GLOSSARY.cuttingSeconds} /></dt><dd>{quantity(run.feature.cuttingSeconds, "초")}</dd></div>
          <div><dt>유휴 시간<HelpTip text={GLOSSARY.idleSeconds} /></dt><dd>{quantity(run.feature.idleSeconds, "초")}</dd></div>
          <div><dt>상태 coverage<HelpTip text={GLOSSARY.stateCoverage} /></dt><dd>{coverage(run.feature.coverageRatio)}</dd></div>
        </dl>
        {run.feature.metrics.map((metric, index) => <article className="metric-card" key={index}>
          <h4>{metricLabel(metric.metric)} · {metric.component ?? "채널 미확인"} · {metric.sourceDataItem}</h4>
          <p>{dataStatusLabel(metric.status)} · coverage {coverage(metric.coverageRatio)}<HelpTip text={GLOSSARY.metricCoverage} /></p>
          <dl className="definition-list">
            <div><dt>시간 가중 평균<HelpTip text={GLOSSARY.timeWeightedMean} /></dt><dd>{quantity(metric.mean, metric.unit)}</dd></div>
            <div><dt>최대<HelpTip text={GLOSSARY.maximum} /></dt><dd>{quantity(metric.maximum, metric.unit)}</dd></div>
            <div><dt>표준편차<HelpTip text={GLOSSARY.standardDeviation} /></dt><dd>{quantity(metric.standardDeviation, metric.unit)}</dd></div>
          </dl>
          <TraceDetails title="측정값 계산 근거" helpText={GLOSSARY.rawEvidence} entries={metric.evidence} />
        </article>)}
        <TraceDetails title="특징 계산·단위·coverage·출처" helpText={GLOSSARY.rawEvidence} entries={run.feature.evidence} />
      </>}
    </section>
    <section className="run-detail-block" aria-label="ANOMALY · 이전 가공과의 차이">
      <h3>ANOMALY · 이전 가공과의 차이<HelpTip text={GLOSSARY.anomalyPurpose} /></h3>
      {!run.assessment ? <p className="empty-state">완료된 가공만 분석 가능</p> : <>
        <p className="status-badge">{dataStatusLabel(run.assessment.status)}</p>
        {run.assessment.status !== "AVAILABLE" && (
          <p className="section-note">{assessmentUnavailableExplanation(run.program, run.assessment)}</p>
        )}
        {run.assessment.score != null && <p className="anomaly-score">차이 점수 {run.assessment.score} · {classificationLabel(run.assessment.classification)}<HelpTip text={GLOSSARY.anomalyScore} /></p>}
        <p className="section-note">같은 프로그램의 이전 가공과 비교한 분석값(DERIVED)이며 고장 판정이 아닙니다.</p>
        <ol className="anomaly-reasons" aria-label="차이의 상위 이유">{run.assessment.reasons.map((reason) => <li key={reason.feature}>
          {featureLabel(reason.feature)}: 관측 기반 값 {reason.target}, 기준선 중앙값 {reason.median}, 차이 {reason.difference}
          {reason.percentage != null ? ` (${reason.percentage}%)` : " · 백분율 차이는 계산할 수 없음"} · 비교 표본 {reason.sampleCount}개 · {reasonLabel(reason.code)}
          <HelpTip text={GLOSSARY.baselineMedianSpread} />
        </li>)}</ol>
        <TraceDetails title="기준선·비교 구간·기여 항목·출처" helpText={GLOSSARY.rawEvidence} entries={run.assessment.evidence} />
      </>}
    </section>
  </article>;
}

export function confidenceLabel(level: string): string {
  return ({ HIGH: "높음", MEDIUM: "중간", LOW: "낮음", UNKNOWN: "미확인" } as Record<string, string>)[level] ?? level;
}
export function classificationLabel(value: string | null | undefined): string {
  return ({ NORMAL: "기준선 범위", DEVIATING: "차이 있음", HIGH_DEVIATION: "큰 차이" } as Record<string, string>)[value ?? ""] ?? "미확인";
}
function featureLabel(key: string): string {
  return ({ durationSeconds: "가공 구간 (초)", cuttingSeconds: "절삭 시간 (초)", idleSeconds: "유휴 시간 (초)" } as Record<string, string>)[key] ?? key;
}
function reasonLabel(code: string): string {
  return ({ EXECUTION_START_CONFIRMED: "실행 시작 관측 확인", START_BOUNDARY_UNCERTAIN: "시작 경계 불확실",
    PROGRAM_MISSING: "프로그램 관측 없음", PROGRAM_OBSERVED: "프로그램 관측 확인",
    POSITIVE_SPINDLE_OBSERVED: "양수 회전수 관측 확인", POSITIVE_SPINDLE_MISSING: "양수 회전수 관측 없음",
    PROGRAM_CHANGED_DURING_RUN: "가공 중 프로그램 변경", END_BOUNDARY_INCOMPLETE: "종료 경계 근거 부족",
    BASELINE_IQR_DISTANCE: "이전 값들의 중앙값과 산포를 기준으로 비교",
    ZERO_IQR_DEVIATION: "이전 값들의 산포가 0이므로 절대 차이로 비교",
  } as Record<string, string>)[code] ?? code;
}

export function TraceDetails({ title, helpText, entries, observed = false }: {
  title: string; helpText?: string; entries: TraceEntry[]; observed?: boolean;
}) {
  return <details className="provenance-disclosure"><summary>{title}{helpText && <HelpTip text={helpText} />}</summary>
    {observed && <>
      <p>OBSERVED · REAL:NIST</p>
      <p className="section-note">실제 NIST Mazak 설비에서 측정된 값이며, ForgeSync가 계산해서 만든 값이 아닙니다.</p>
    </>}
    <dl className="definition-list process-evidence">{entries.map((entry, index) =>
      <div key={index}><dt>{entry.label}</dt><dd>{entry.value}</dd></div>)}</dl>
  </details>;
}

function quantity(value: number | undefined | null, unit = "단위 미확인"): string {
  return value == null ? "데이터 없음" : `${value} ${unit}`;
}
function coverage(ratio: number | null): string {
  return ratio === null ? "확인할 수 없음" : `${Math.round(ratio * 1_000_000) / 10_000}%`;
}
function metricLabel(metric: string): string {
  return ({ SPINDLE_SPEED: "회전수", LOAD: "부하", PATH_FEEDRATE: "이송" } as Record<string, string>)[metric] ?? metric;
}
function dataStatusLabel(status: string): string {
  return ({ AVAILABLE: "분석 가능", PARTIAL: "일부 데이터 부족", MISSING: "데이터 없음",
    EMPTY_WINDOW: "빈 분석 구간", INSUFFICIENT_DATA: "비교 표본 부족", UNAVAILABLE: "비교할 수 없음" } as Record<string, string>)[status] ?? status;
}
function formatUtc(value: string): string {
  return value.replace("T", " ").replace("Z", " UTC");
}
