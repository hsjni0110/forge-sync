import { useState } from "react";
import type { ReplaySessionState } from "../../replay/domain/replay";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import { HttpProcessAnalysisClient } from "../adapters/httpProcessAnalysisClient";
import type { ProcessAnalysisClient } from "../application/ports";
import { currentRun, runStatusLabel, sampleProgressLabel, type MachiningRun, type RunAnalysis } from "../domain/processAnalysis";
import { GLOSSARY } from "../domain/processGlossary";
import { classificationLabel, confidenceLabel, RunDetail } from "./RunDetail";
import { HelpTip } from "./HelpTip";
import { useProcessAnalysis } from "./useProcessAnalysis";

const browserClient = new HttpProcessAnalysisClient(import.meta.env.VITE_API_BASE_URL ?? "");

export function ProcessAnalysisPanel({ machineId, session, twinState, retryTwin, reloadReplay, seek,
  client = browserClient, layout = "FULL",
}: {
  machineId: string; session?: ReplaySessionState; twinState: TwinLiveState;
  retryTwin: () => void; reloadReplay: () => Promise<void>; seek: (sourceObservedAt: string) => void;
  client?: ProcessAnalysisClient;
  layout?: "FULL" | "COMPACT";
}) {
  const { analysis, message, canRetry, retry } = useProcessAnalysis({
    machineId, session, twinState, client, retryTwin, reloadReplay,
  });
  const [selection, setSelection] = useState<{ analysis: RunAnalysis; runId: string }>();
  const cursor = twinState.snapshot?.replayCursor;
  const current = analysis && cursor ? currentRun(analysis.runs, cursor) : undefined;
  const selected = selection?.analysis === analysis
    ? analysis?.runs.find((run) => run.id === selection?.runId) : current;
  return <div className="process-analysis" data-process-version={analysis ? cursor?.twinVersion : undefined}
    data-process-session={analysis ? cursor?.replaySessionId : undefined}>
    {layout === "FULL" && <div className="process-glossary">
      <strong>이 화면을 읽는 방법</strong>
      <dl>
        <div><dt>OBSERVED</dt><dd>{GLOSSARY.observed}</dd></div>
        <div><dt>DERIVED</dt><dd>{GLOSSARY.derived}</dd></div>
        <div><dt>신뢰도</dt><dd>{GLOSSARY.confidence}</dd></div>
        <div><dt>coverage</dt><dd>{GLOSSARY.coverageOverview}</dd></div>
        <div><dt>기준선</dt><dd>{GLOSSARY.baseline}</dd></div>
      </dl>
    </div>}
    <section className="detail-section" aria-label="CURRENT RUN · 현재 가공">
      <h2>CURRENT RUN · 현재 가공</h2>
      {!analysis ? <p role="status">{message}</p> : current ? <>
        <p className="run-detail-badges">
          <span className="status-badge" data-status="active">{runStatusLabel(current)}</span>
          <span className="provenance-tag">DERIVED</span><HelpTip text={GLOSSARY.derived} />
        </p>
        <dl className="definition-list">
          <div><dt>프로그램<HelpTip text={GLOSSARY.program} /></dt><dd>{current.program ?? "확인할 수 없음"}</dd></div>
          <div><dt>시작 시각</dt><dd>{current.startedAt}</dd></div>
          <div><dt>신뢰도<HelpTip text={GLOSSARY.confidence} /></dt><dd>{confidenceLabel(current.confidence)}</dd></div>
        </dl>
      </> : <p>현재 가공 없음</p>}
      <button type="button" className="button-quiet" disabled={!canRetry} onClick={retry}>{analysis ? "분석 다시 계산" : "분석 다시 시도"}</button>
      {analysis && <p className="section-note">분석 기준 · {cursor?.sourceObservedAt} · Twin v{cursor?.twinVersion}</p>}
    </section>
    {layout === "COMPACT" && <div className="process-summary">
      <p>PROCESS / ANOMALY · {current ? "완료된 가공만 분석 가능" : "현재 가공의 분석 없음"}</p>
      <p className="section-note">상단 2D 보기에서 가공 목록과 이상 근거를 확인할 수 있습니다.</p>
    </div>}
    {analysis && layout === "FULL" && <section className="detail-section" aria-label="가공 목록과 상세">
      <h2>Process Timeline · 가공 목록</h2>
      <p className="section-note">가공을 하나 선택하면 근거와 이전 가공과의 차이를 볼 수 있습니다. 선택만으로는 재생 위치가 바뀌지 않습니다.</p>
      <RunTimelineOverview runs={analysis.runs} selectedId={selected?.id} cursorAt={cursor?.sourceObservedAt}
        onSelect={(runId) => setSelection({ analysis, runId })} />
      <p className="section-note">막대는 실제 가공 시간, 막대 사이 빈 공간은 유휴 시간입니다. 노란 선은 현재 재생 위치입니다.</p>
      <ol className="run-timeline" aria-label="가공 타임라인">
        {analysis.runs.map((run) => {
          const badge = runTimelineBadge(run);
          const duration = run.endedAt ? (Date.parse(run.endedAt) - Date.parse(run.startedAt)) / 1000 : undefined;
          const compare = durationComparison(run);
          return <li key={run.id}>
            <button type="button" className="run-timeline-item" aria-pressed={selected?.id === run.id}
              data-classification={badge.tone}
              aria-label={`가공 선택 · ${run.program ?? "프로그램 미확인"} · ${run.startedAt} → ${run.endedAt ?? "종료 근거 미확정"}`}
              onClick={() => setSelection({ analysis, runId: run.id })}>
              <span className="run-row-main">
                <span className="run-timeline-badge">{badge.label}</span>
                {badge.detail && <span className="run-timeline-badge-detail">{badge.detail}</span>}
                <span className="run-row-program">PGM {run.program ?? "미확인"}</span>
                <span className="run-row-time">
                  {formatTimeOfDay(run.startedAt)} → {run.endedAt ? formatTimeOfDay(run.endedAt) : "종료 미확인"}
                  <small>{formatDateOnly(run.startedAt)}</small>
                </span>
                <span className="run-row-spacer" />
                {duration !== undefined && <span className="run-row-duration">{formatDuration(duration)}<small>가공 시간</small></span>}
              </span>
              {compare && <RunRowCompare compare={compare} />}
            </button>
          </li>;
        })}
      </ol>
      {selected && <div className="run-seek-actions" role="group" aria-label="선택한 가공 시점으로 재생 이동">
        <button type="button" className="button-quiet" onClick={() => seek(selected.startedAt)}>가공 시작 시점으로 이동</button>
        {selected.endedAt && <button type="button" className="button-quiet" onClick={() => seek(selected.endedAt!)}>가공 종료 시점으로 이동</button>}
      </div>}
      {selected ? <RunDetail run={selected} /> : <p className="empty-state">위 목록에서 가공을 선택해 분석 근거를 확인하세요.</p>}
      <details className="provenance-disclosure"><summary>분석 처리 버전</summary><dl className="definition-list">
        <div><dt>가공 분할 1.0.0</dt><dd>{analysis.processingId}</dd></div>
        <div><dt>특징 계산 1.0.0</dt><dd>{analysis.featureProcessingId}</dd></div>
        <div><dt>이상 평가 1.0.0 · 기준선 1.0.0</dt><dd>{analysis.assessmentProcessingId}</dd></div>
      </dl></details>
    </section>}
  </div>;
}

const OVERVIEW_MIN_WIDTH_PERCENT = 2;
const OVERVIEW_TICK_COUNT = 4;
const OVERVIEW_LABEL_MIN_WIDTH_PERCENT = 12;

function RunTimelineOverview({ runs, selectedId, onSelect, cursorAt }: {
  runs: MachiningRun[]; selectedId?: string; onSelect: (runId: string) => void; cursorAt?: string;
}) {
  if (runs.length === 0) return null;
  const cursorTime = cursorAt ? Date.parse(cursorAt) : undefined;
  const startTimes = runs.map((run) => Date.parse(run.startedAt));
  const endTimes = runs.map((run) => Date.parse(run.endedAt ?? run.startedAt));
  const knownTimes = cursorTime === undefined ? [...startTimes, ...endTimes] : [...startTimes, ...endTimes, cursorTime];
  const rangeStart = Math.min(...knownTimes);
  const rangeEnd = Math.max(...knownTimes, rangeStart + 60_000);
  const span = rangeEnd - rangeStart;
  const percent = (time: number) => ((time - rangeStart) / span) * 100;
  const ticks = Array.from({ length: OVERVIEW_TICK_COUNT + 1 }, (_, index) => rangeStart + (span * index) / OVERVIEW_TICK_COUNT);
  const cursorLeft = cursorTime !== undefined ? percent(cursorTime) : undefined;
  const cursorLabelLeft = cursorLeft !== undefined ? Math.min(94, Math.max(6, cursorLeft)) : undefined;
  return (
    <div className="run-overview" role="group" aria-label="가공 시간대 개요 · 미리보기">
      {cursorTime !== undefined && cursorLabelLeft !== undefined && (
        <div className="run-overview-now-row" aria-hidden="true">
          <span className="run-overview-now-label" style={{ left: `${cursorLabelLeft}%` }}>
            지금 {formatClockShort(cursorTime)}
          </span>
        </div>
      )}
      <div className="run-overview-track">
        <div className="run-overview-gridlines" aria-hidden="true">
          {ticks.map((time, index) => (
            <span key={index} className="run-overview-gridline" style={{ left: `${percent(time)}%` }} />
          ))}
        </div>
        <div className="run-overview-baseline" aria-hidden="true" />
        {ticks.map((time, index) => (
          <span key={`tick-${index}`} className="run-overview-tick" aria-hidden="true" style={{ left: `${percent(time)}%` }} />
        ))}
        {runs.map((run) => {
          const badge = runTimelineBadge(run);
          const start = Date.parse(run.startedAt);
          const end = run.endedAt ? Date.parse(run.endedAt) : rangeEnd;
          const left = percent(start);
          const width = Math.max(OVERVIEW_MIN_WIDTH_PERCENT, percent(end) - left);
          const durationSeconds = run.endedAt ? (end - start) / 1000 : undefined;
          return (
            <button type="button" key={run.id} className="run-overview-bar"
              data-classification={badge.tone} aria-pressed={selectedId === run.id}
              style={{ left: `${left}%`, width: `${width}%` }}
              title={`미리보기 · ${run.program ?? "프로그램 미확인"} · ${badge.label}`}
              aria-label={`가공 미리보기 · ${run.program ?? "프로그램 미확인"} · ${badge.label}`}
              onClick={() => onSelect(run.id)}>
              {width >= OVERVIEW_LABEL_MIN_WIDTH_PERCENT && durationSeconds !== undefined && (
                <span className="run-overview-bar-label">{formatDuration(durationSeconds)}</span>
              )}
            </button>
          );
        })}
        {cursorTime !== undefined && (
          <div className="run-overview-cursor" aria-hidden="true" style={{ left: `${percent(cursorTime)}%` }} />
        )}
      </div>
      <div className="run-overview-axis">
        {ticks.map((time, index) => (
          <span key={index}>{formatClockShort(time)}{index === ticks.length - 1 ? " UTC" : ""}</span>
        ))}
      </div>
    </div>
  );
}

function formatClockShort(ms: number): string {
  const date = new Date(ms);
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

function formatTimeOfDay(iso: string): string {
  const date = new Date(iso);
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`;
}

function formatDateOnly(iso: string): string {
  return iso.slice(0, 10);
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function formatDuration(seconds: number): string {
  const rounded = Math.round(seconds);
  if (rounded < 60) return `${rounded}초`;
  const minutes = Math.floor(rounded / 60);
  const remainder = rounded % 60;
  return remainder === 0 ? `${minutes}분` : `${minutes}분 ${remainder}초`;
}

function durationComparison(run: MachiningRun): DurationComparison | undefined {
  if (run.assessment?.status !== "AVAILABLE") return undefined;
  const reason = run.assessment.reasons.find((item) => item.feature === "durationSeconds");
  return reason && { current: reason.target, median: reason.median, percentage: reason.percentage ?? null,
    distance: reason.distance ?? null, scale: reason.scale ?? null };
}

interface DurationComparison {
  current: number; median: number; percentage: number | null;
  distance: number | null; scale: number | null;
}

function RunRowCompare({ compare }: { compare: DurationComparison }) {
  const scale = Math.max(compare.current, compare.median, 1) * 1.2;
  const barWidth = (compare.current / scale) * 100;
  const markerLeft = (compare.median / scale) * 100;
  return (
    <span className="run-row-compare">
      <span>가공 구간 비교</span>
      <span className="run-row-compare-bar" aria-hidden="true">
        <span className="run-row-compare-fill" style={{ width: `${barWidth}%` }} />
        <span className="run-row-compare-marker" style={{ left: `${markerLeft}%` }} />
      </span>
      <span>
        이번 {formatDuration(compare.current)} · 기준(중앙값) {formatDuration(compare.median)}
        {compare.percentage != null && <> · <strong>{formatPercentage(compare.percentage)}</strong></>}
        {compare.distance != null && compare.scale != null && (
          <> · 정상 폭 {formatDuration(compare.scale)}의 <strong>{formatMultiple(compare.distance)}</strong></>
        )}
      </span>
    </span>
  );
}

function formatPercentage(value: number): string {
  return `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
}

// 편차를 정상 폭의 배수로 표시해, 같은 등급 안에서도 크기를 구분할 수 있게 한다.
function formatMultiple(distance: number): string {
  return `${distance.toFixed(1)}배`;
}

function runTimelineBadge(run: MachiningRun): { label: string; tone?: "deviating" | "high_deviation"; detail?: string } {
  if (run.status !== "COMPLETED" || run.endedAt === undefined) {
    return { label: runStatusLabel(run) };
  }
  const classification = run.assessment?.status === "AVAILABLE" ? run.assessment.classification : undefined;
  if (classification) {
    return {
      label: classificationLabel(classification),
      tone: classification === "HIGH_DEVIATION" ? "high_deviation" : classification === "DEVIATING" ? "deviating" : undefined,
    };
  }
  return { label: "비교 불가", detail: sampleProgressLabel(run.assessment) };
}
