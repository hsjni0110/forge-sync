import { useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import { formatDecimal, formatUtcParts } from "../../../shared/presentation/valueFormatters";
import type { Freshness } from "../../twin/domain/twin";
import type { ReplayStatus } from "../../replay/domain/replay";
import type { ShiftInterval, ShiftMarker, ShiftOverview, ShiftState } from "../domain/shiftOverview";
import { timelinePercent, timelineTicks } from "../domain/shiftOverview";
import type { Alarm } from "../../alarm/domain/alarm";
import type { DowntimeParetoEntry } from "../../downtime/domain/downtimePareto";
import { longestStops } from "../../downtime/domain/downtimePareto";
import { concurrentFactLabel, controllerModeLabel } from "../../downtime/ui/concurrentFactLabel";

const STATE_LABELS: Record<ShiftState, string> = {
  ACTIVE: "작업",
  READY: "대기",
  STOPPED: "멈춤",
  INTERRUPTED: "작업 중단",
  UNKNOWN: "기록 없음",
};

const LEGEND_STATES: ShiftState[] = ["ACTIVE", "STOPPED", "UNKNOWN"];

interface TimelineView { from: string; to: string }

export function ShiftOverviewPanel({
  report,
  freshness,
  replayStatus,
  onSeek,
  alarms = [],
  children,
}: {
  report: ShiftOverview;
  freshness?: Freshness;
  replayStatus?: ReplayStatus;
  onSeek: (sourceObservedAt: string) => void;
  alarms?: Alarm[];
  /** Secondary analyses folded under "자세히 보기" so the first read stays on one question. */
  children?: ReactNode;
}) {
  const [focused, setFocused] = useState<DowntimeParetoEntry>();
  const [showsAlarms, setShowsAlarms] = useState(false);
  const [showsToolChanges, setShowsToolChanges] = useState(false);
  // The day level offers the three longest stops: one as the main action, two as alternatives.
  const stops = longestStops(report.pareto.entries, 3);
  const [stop, ...otherStops] = stops;
  const stepped = (offset: number) => {
    const index = stops.findIndex((entry) => entry.startedAt === focused?.startedAt);
    return stops[(index + offset + stops.length) % stops.length];
  };
  const view = focused ? focusView(focused) : { from: report.observedFrom, to: report.observedTo };
  return (
    <section className="shift-overview" aria-labelledby="shift-overview-title">
      <ScopeNav report={report} focused={focused} onBack={() => setFocused(undefined)} />
      {focused ? <StopHeadline entry={focused} /> : <DayHeadline report={report} stop={stop} />}

      <ShiftTimeline report={report} view={view} onSeek={onSeek}
        alarms={showsAlarms ? alarms : []} markers={showsToolChanges ? report.markers : []}
        outlined={focused ? undefined : stop} onOutlinedSelect={setFocused}
        pickable={focused ? recordedPart(view, report) : undefined}
        layerToggles={<>
          <LayerToggle label="공구 교체" count={report.markers.length} pressed={showsToolChanges}
            onToggle={() => setShowsToolChanges(!showsToolChanges)} />
          <LayerToggle label="알람" count={alarms.length} pressed={showsAlarms}
            onToggle={() => setShowsAlarms(!showsAlarms)} />
        </>} />
      {showsAlarms && !focused && <AlarmSummary alarms={alarms} onSeek={onSeek} />}

      {focused ? <StopFacts entry={focused} /> : <DayFacts report={report} />}

      <div className="shift-focus-actions">
        {focused ? <>
          <p className="shift-focus-hint">막대를 누르거나 끌면 그 순간의 기계를 볼 수 있어요.</p>
          <button type="button" className="shift-ghost shift-step"
            onClick={() => setFocused(stepped(-1))}>← 이전</button>
          <button type="button" className="shift-ghost" onClick={() => setFocused(stepped(1))}>다음 →</button>
        </> : stop && <>
          <button type="button" className="shift-focus-cta" onClick={() => setFocused(stop)}>
            가장 오래 멈춘 때 보기
          </button>
          {otherStops.length > 0 && (
            <div className="shift-other-stops" role="group" aria-label="다른 긴 멈춤">
              <span aria-hidden="true">다른 긴 멈춤</span>
              {otherStops.map((entry) => (
                <button key={entry.startedAt} type="button" className="shift-ghost"
                  onClick={() => setFocused(entry)}>{minute(entry.startedAt)}</button>
              ))}
            </div>
          )}
        </>}
      </div>

      {!focused && (
        <details className="shift-details">
          <summary>자세히 보기</summary>
          <section className="shift-kpi-band" aria-label="그 밖의 지표">
            <ShiftKpi label="절삭 비율" value={percent(report.cuttingPercent)} provenance="파생" />
            <ShiftKpi label="가공 건수" value={`전체 ${report.totalMachiningCount}건`}
              detail={`완료 ${report.completedMachiningCount}건`} provenance="파생" />
            <ShiftKpi label="데이터 최신성" value={freshnessLabel(freshness, replayStatus)}
              provenance="관측" />
          </section>
          {children}
        </details>
      )}
    </section>
  );
}

function ScopeNav({ report, focused, onBack }: {
  report: ShiftOverview; focused?: DowntimeParetoEntry; onBack: () => void;
}) {
  return (
    <nav className="shift-scope" aria-label="보는 범위">
      <span>{report.machineId} · {koreanDate(report.observedFrom)}</span>
      <span aria-hidden="true">/</span>
      {focused
        ? <button type="button" onClick={onBack}>하루 전체</button>
        : <span aria-current="page">하루 전체</span>}
      {focused && <>
        <span aria-hidden="true">/</span>
        <span className="shift-scope-mono" aria-current="page">
          {minute(focused.startedAt)}–{minute(focused.endedAt)}
        </span>
      </>}
      <span className="shift-scope-zone">시각은 모두 UTC 기준</span>
    </nav>
  );
}

function DayHeadline({ report, stop }: { report: ShiftOverview; stop?: DowntimeParetoEntry }) {
  return (
    <header className="shift-overview-heading">
      <p className="shift-overview-range">
        하루 전체 · {minute(report.observedFrom)}–{minute(report.observedTo)} · {duration(
          (Date.parse(report.observedTo) - Date.parse(report.observedFrom)) / 1000)}
      </p>
      <h2 id="shift-overview-title">{[
        report.availabilityPercent === undefined
          ? "작업한 시간을 계산할 근거가 부족해요."
          : `기계가 실제로 작업한 시간은 하루의 ${percent(report.availabilityPercent)}예요.`,
        stop
          ? `가장 오래 멈춘 때는 ${minute(stop.startedAt)}부터 ${minute(stop.endedAt)}까지예요.`
          : "멈춤으로 기록된 때는 없어요.",
      ].join(" ")}</h2>
    </header>
  );
}

function StopHeadline({ entry }: { entry: DowntimeParetoEntry }) {
  const modeChange = entry.evidence.find((evidence) =>
    evidence.kind === "MODE_CHANGE" && evidence.value !== "UNKNOWN");
  const length = duration(entry.durationSeconds);
  return (
    <header className="shift-overview-heading">
      <p className="shift-overview-range">{minute(entry.startedAt)}–{minute(entry.endedAt)} 구간</p>
      <h2 id="shift-overview-title">{[
        entry.state === "INTERRUPTED"
          ? `${length} 동안 작업이 중단돼 있었어요.` : `${length} 동안 멈춰 있었어요.`,
        // A mode change recorded alongside is a concurrent fact, never stated as the cause.
        modeChange && `같은 시간에 ${controllerModeLabel(modeChange.value)}로 바뀐 기록이 있어요.`,
      ].filter(Boolean).join(" ")}</h2>
      <p>함께 기록된 사실만 보여 드려요. 멈춘 이유를 확인하려면 현장 작업 기록과 함께 보세요.</p>
    </header>
  );
}

function DayFacts({ report }: { report: ShiftOverview }) {
  return (
    <ul className="shift-facts" aria-label="하루 요약">
      <Fact state="ACTIVE" label="작업" value={percent(report.availabilityPercent)}
        note={report.activeSeconds === undefined ? undefined : duration(report.activeSeconds)} />
      <Fact state="STOPPED" label="멈춤" value={duration(report.stoppedSeconds)}
        note="멈춤과 작업 중단을 합친 시간" />
      <Fact state="UNKNOWN" label="기록 없음" value={duration(report.unknownSeconds)}
        note="데이터가 끊긴 시간이라 멈춤에 넣지 않았어요" />
    </ul>
  );
}

function StopFacts({ entry }: { entry: DowntimeParetoEntry }) {
  const modeChanges = entry.evidence.filter((evidence) => evidence.kind === "MODE_CHANGE");
  const others = entry.evidence.filter((evidence) => evidence.kind !== "MODE_CHANGE");
  return (
    <ul className="shift-facts" aria-label="멈춘 때 요약">
      <Fact label="길이" value={duration(entry.durationSeconds)} />
      <Fact state={entry.state} label="상태" value={STATE_LABELS[entry.state]} />
      <Fact label="운전 모드" value={modeChanges.length > 0
        ? modeChanges.map(concurrentFactLabel).join(" · ") : "바뀐 기록 없음"}
        note={modeChanges.length > 0 ? "같은 시간에 기록됨" : undefined} />
      <Fact label="경고·비상정지" value={others.length > 0
        ? others.map(concurrentFactLabel).join(" · ") : "없음"}
        note={others.length > 0 ? "같은 시간에 기록됨" : undefined} />
    </ul>
  );
}

function LayerToggle({ label, count, pressed, onToggle }: {
  label: string; count: number; pressed: boolean; onToggle: () => void;
}) {
  if (count === 0) return null;
  return (
    <button type="button" className="shift-layer-toggle" aria-pressed={pressed} onClick={onToggle}>
      {pressed ? `${label} 숨기기` : `${label} ${count}건 함께 보기`}
    </button>
  );
}

function ShiftTimeline({ report, view, alarms, markers, onSeek, outlined, onOutlinedSelect,
  pickable, layerToggles }: {
  report: ShiftOverview; view: TimelineView; alarms: Alarm[]; markers: ShiftMarker[];
  onSeek: (sourceObservedAt: string) => void;
  outlined?: DowntimeParetoEntry; onOutlinedSelect: (entry: DowntimeParetoEntry) => void;
  pickable?: TimelineView; layerToggles: ReactNode;
}) {
  return (
    <section className="shift-timeline-section" aria-label="시간 막대">
      <div className="shift-timeline-ticks" aria-hidden="true">
        {/* Labels centred on the very edge would overhang the bar, so those are left out. */}
        {timelineTicks(view.from, view.to).filter((tick) => tick.percent > 3 && tick.percent < 97).map((tick) => (
          <span key={tick.label} style={{ left: `${tick.percent}%` }}>{tick.label}</span>
        ))}
      </div>
      <section className="shift-timeline" aria-label="설비 상태 구간">
        {/* Runs are clipped to the rounded track; marks sit above it, outside the clip. */}
        <div className="shift-timeline-runs">
          {report.intervals.filter((interval) => overlaps(interval, report, view)).map((interval) => (
            // A stable key lets the same bar slide into the focused range instead of re-mounting.
            <IntervalButton key={`${interval.startedAt}-${interval.state}`} interval={interval}
              report={report} view={view} onSeek={onSeek} />
          ))}
          {outlined && <OutlinedStop entry={outlined} view={view} onSelect={onOutlinedSelect} />}
        </div>
        {pickable && <MomentPicker range={pickable} view={view} onSeek={onSeek} />}
        {markers.filter((marker) => isInView(marker.sourceObservedAt, view)).map((marker, index) => {
          const left = timelinePercent(marker.sourceObservedAt, view.from, view.to);
          return (
            <button key={`${marker.kind}-${marker.sourceObservedAt}-${index}`} type="button"
              className="shift-marker" data-kind={marker.kind} style={{ left: `${left}%` }}
              onClick={() => onSeek(marker.seekTo)} aria-label={marker.label}>
              <span aria-hidden="true">T</span>
            </button>
          );
        })}
        {alarms.filter((alarm) => isInView(alarm.openedAt, view)).map((alarm) => {
          const left = timelinePercent(alarm.openedAt, view.from, view.to);
          const severity = alarm.severity === "CRITICAL" ? "긴급" : "주의";
          return (
            <button key={alarm.alarmId} type="button" className="shift-marker"
              data-kind="ALARM" data-severity={alarm.severity} data-alarm-id={alarm.alarmId}
              style={{ left: `${left}%` }} onClick={() => onSeek(alarm.openedAt)}
              aria-label={`${severity} 알람 · 코드 ${alarm.nativeCode} · ${alarm.message ?? alarm.conditionType}`}>
              <WarningIcon />
            </button>
          );
        })}
      </section>
      <div className="shift-timeline-legend">
        {/* READY is drawn as the empty track, so it is named instead of given a swatch. */}
        <ul className="shift-state-legend" aria-label="상태 범례">
          {LEGEND_STATES.map((state) => (
            <li key={state} data-state={state}><span aria-hidden="true" />{STATE_LABELS[state]}</li>
          ))}
          <li className="shift-state-legend-note">빈 곳은 대기</li>
        </ul>
        <div className="shift-layer-toggles">{layerToggles}</div>
      </div>
    </section>
  );
}

/** The zoomed view pads past the recording; only recorded instants can be replayed. */
function recordedPart(view: TimelineView, report: ShiftOverview): TimelineView {
  return {
    from: Date.parse(view.from) < Date.parse(report.observedFrom) ? report.observedFrom : view.from,
    to: Date.parse(view.to) > Date.parse(report.observedTo) ? report.observedTo : view.to,
  };
}

/** Dragging only previews the instant; the replay seeks once, when the pointer or Enter lets go. */
function MomentPicker({ range, view, onSeek }: {
  range: TimelineView; view: TimelineView; onSeek: (sourceObservedAt: string) => void;
}) {
  const navigate = useNavigate();
  const [pickedSeconds, setPickedSeconds] = useState<number>();
  const min = Math.ceil(Date.parse(range.from) / 1000);
  const max = Math.floor(Date.parse(range.to) / 1000);
  const left = timelinePercent(range.from, view.from, view.to);
  const right = timelinePercent(range.to, view.from, view.to);
  const picked = pickedSeconds === undefined ? undefined : new Date(pickedSeconds * 1000).toISOString();
  const open = () => {
    if (!picked) return;
    onSeek(picked);
    navigate("/factory");
  };
  return <>
    <input type="range" className="shift-moment-picker" aria-label="볼 시각 고르기"
      min={min} max={max} step={1} value={pickedSeconds ?? min}
      style={{ left: `${left}%`, width: `${right - left}%` }}
      onChange={(event) => setPickedSeconds(Number(event.target.value))}
      onPointerUp={open} onKeyUp={(event) => { if (event.key === "Enter") open(); }} />
    {picked && (
      <span className="shift-moment-cursor" style={{ left: `${timelinePercent(picked, view.from, view.to)}%` }}>
        <span>{clock(picked)}</span>
      </span>
    )}
  </>;
}

function OutlinedStop({ entry, view, onSelect }: {
  entry: DowntimeParetoEntry; view: TimelineView; onSelect: (entry: DowntimeParetoEntry) => void;
}) {
  const start = timelinePercent(entry.startedAt, view.from, view.to);
  const end = timelinePercent(entry.endedAt, view.from, view.to);
  return (
    <button type="button" className="shift-outlined-stop"
      style={{ left: `${start}%`, width: `${Math.max(0.5, end - start)}%` }}
      onClick={() => onSelect(entry)}
      aria-label={`${minute(entry.startedAt)}–${minute(entry.endedAt)} 자세히 보기`} />
  );
}

function AlarmSummary({ alarms, onSeek }: {
  alarms: Alarm[]; onSeek: (sourceObservedAt: string) => void;
}) {
  return (
    <section className="shift-alarm-summary" aria-label="확인할 알람">
      <header>
        <h3>확인할 알람</h3>
        <span>{alarms.length}건</span>
      </header>
      <ul>
        {alarms.map((alarm) => (
          <li key={alarm.alarmId} data-severity={alarm.severity}>
            <button type="button" data-alarm-id={alarm.alarmId} onClick={() => onSeek(alarm.openedAt)}
              aria-label={`알람 상세로 이동 · 코드 ${alarm.nativeCode} · ${clock(alarm.openedAt)} UTC`}>
              <WarningIcon />
              <span>
                <strong>{alarm.severity === "CRITICAL" ? "긴급 알람" : "주의 알람"}</strong>
                <small>{alarm.nativeCode} · {alarm.message ?? alarm.conditionType}</small>
              </span>
              <time dateTime={alarm.openedAt}>{clock(alarm.openedAt)} UTC</time>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Half the stop's length on each side keeps what came before and after it in view. */
function focusView(entry: DowntimeParetoEntry): TimelineView {
  const start = Date.parse(entry.startedAt);
  const end = Date.parse(entry.endedAt);
  const padding = (end - start) / 2;
  return { from: new Date(start - padding).toISOString(), to: new Date(end + padding).toISOString() };
}

function isInView(instant: string, view: TimelineView): boolean {
  const value = Date.parse(instant);
  return value >= Date.parse(view.from) && value <= Date.parse(view.to);
}

function overlaps(interval: ShiftInterval, report: ShiftOverview, view: TimelineView): boolean {
  const end = Date.parse(interval.endedAt ?? report.observedTo);
  return Date.parse(interval.startedAt) < Date.parse(view.to) && end > Date.parse(view.from);
}

function IntervalButton({ interval, report, view, onSeek }: {
  interval: ShiftInterval; report: ShiftOverview; view: TimelineView;
  onSeek: (sourceObservedAt: string) => void;
}) {
  const start = timelinePercent(interval.startedAt, view.from, view.to);
  const end = timelinePercent(interval.endedAt ?? report.observedTo, view.from, view.to);
  const endLabel = interval.endedAt ? clock(interval.endedAt) : "종료 근거 없음";
  return (
    <button type="button" className="shift-interval" data-state={interval.state}
      style={{ left: `${start}%`, width: `${Math.max(0.25, end - start)}%` }}
      onClick={() => onSeek(interval.startedAt)}
      aria-label={`${STATE_LABELS[interval.state]} ${clock(interval.startedAt)} - ${endLabel}`}>
      <StateIcon state={interval.state} />
      <span>{STATE_LABELS[interval.state]}</span>
    </button>
  );
}

function StateIcon({ state }: { state: ShiftState }) {
  if (state === "INTERRUPTED") {
    return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M2 2h4v12H2V2Zm8 0h4v12h-4V2Z" />
    </svg>;
  }
  if (state === "UNKNOWN") {
    return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M7.3 10.3h1.4v1.4H7.3v-1.4Zm.7-8a4 4 0 0 1 2.4 7.2c-.9.7-1.7 1-1.7 1.9H7.3c0-1.6 1.1-2.3 2.1-3A2.6 2.6 0 1 0 5.4 6H4a4 4 0 0 1 4-3.7Z" />
    </svg>;
  }
  return null;
}

function WarningIcon() {
  return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
    <path d="M8 1.6 15 14H1L8 1.6Zm0 3L3.4 12.7h9.2L8 4.6ZM7.3 7h1.4v3.2H7.3V7Zm0 4.1h1.4v1.4H7.3v-1.4Z" />
  </svg>;
}

function ShiftKpi({ label, value, detail, provenance }: {
  label: string; value: string; detail?: string; provenance: string;
}) {
  return <div className="shift-kpi"><span>{label}</span><strong>{value}</strong>
    {detail && <small>{detail}</small>}<em>{provenance}</em></div>;
}

function percent(value?: number): string {
  return value === undefined ? "근거 부족" : `${formatDecimal(value, { maximumFractionDigits: 1 })}%`;
}

function duration(seconds: number): string {
  const rounded = Math.round(seconds);
  const hours = Math.floor(rounded / 3600);
  const minutes = Math.floor((rounded % 3600) / 60);
  const remainder = rounded % 60;
  return hours > 0 ? `${hours}시간 ${minutes}분` : minutes > 0 ? `${minutes}분 ${remainder}초` : `${remainder}초`;
}

function clock(value: string): string { return formatUtcParts(value).time; }

function minute(value: string): string { return clock(value).slice(0, 5); }

function koreanDate(value: string): string {
  const [year, month, day] = formatUtcParts(value).date.split("-").map(Number);
  return `${year}년 ${month}월 ${day}일`;
}

function Fact({ state, label, value, note }: {
  state?: string; label: string; value: string; note?: string;
}) {
  return (
    <li data-state={state}>
      <span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}
    </li>
  );
}

function freshnessLabel(freshness?: Freshness, replayStatus?: ReplayStatus): string {
  if (replayStatus === "COMPLETED") return "마지막 재생 데이터";
  if (replayStatus === "PAUSED") return "선택 시점 데이터";
  if (freshness === "FRESH") return "최신";
  if (freshness === "LAGGING") return "지연됨";
  if (freshness === "STALE") return "오래된 데이터";
  return "알 수 없음";
}
