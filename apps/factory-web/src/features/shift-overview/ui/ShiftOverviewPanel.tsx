import { formatDecimal, formatUtcParts } from "../../../shared/presentation/valueFormatters";
import type { Freshness } from "../../twin/domain/twin";
import type { ReplayStatus } from "../../replay/domain/replay";
import type { ShiftInterval, ShiftOverview, ShiftState } from "../domain/shiftOverview";
import { timelinePercent } from "../domain/shiftOverview";
import type { Alarm } from "../../alarm/domain/alarm";

const STATE_LABELS: Record<ShiftState, string> = {
  ACTIVE: "가동",
  READY: "준비",
  STOPPED: "정지",
  INTERRUPTED: "가공 중단",
  UNKNOWN: "확인 불가",
};

export function ShiftOverviewPanel({
  report,
  freshness,
  replayStatus,
  onSeek,
  alarms = [],
}: {
  report: ShiftOverview;
  freshness?: Freshness;
  replayStatus?: ReplayStatus;
  onSeek: (sourceObservedAt: string) => void;
  alarms?: Alarm[];
}) {
  return (
    <section className="shift-overview" aria-labelledby="shift-overview-title">
      <div className="shift-overview-heading">
        <div>
          <h2 id="shift-overview-title">교대조 관측 구간</h2>
          <p>{clock(report.observedFrom)} - {clock(report.observedTo)} UTC</p>
        </div>
        <span className="provenance-chip">같은 Replay 버전</span>
      </div>

      <section className="shift-kpi-band" aria-label="교대조 핵심 지표">
        <ShiftKpi label="가동률" value={percent(report.availabilityPercent)} provenance="파생" />
        <ShiftKpi label="절삭 비율" value={percent(report.cuttingPercent)} provenance="파생" />
        <ShiftKpi label="정지 시간" value={duration(report.downtimeSeconds)} provenance="파생" />
        <ShiftKpi label="가공 건수" value={`전체 ${report.totalMachiningCount}건`}
          detail={`완료 ${report.completedMachiningCount}건`} provenance="파생" />
        <ShiftKpi label="데이터 최신성" value={freshnessLabel(freshness, replayStatus)}
          provenance="관측" />
      </section>

      <section className="shift-timeline-section" aria-labelledby="shift-timeline-title">
        <div className="shift-timeline-heading">
          <h3 id="shift-timeline-title">설비 상태 흐름</h3>
          <p>색과 상태 이름을 함께 표시합니다.</p>
        </div>
        <section className="shift-timeline" aria-label="설비 상태 구간">
          {report.intervals.map((interval, index) => (
            <IntervalButton key={`${interval.startedAt}-${index}`} interval={interval} report={report}
              onSeek={onSeek} />
          ))}
          {report.markers.map((marker, index) => {
            const left = timelinePercent(marker.sourceObservedAt, report.observedFrom, report.observedTo);
            return (
              <button key={`${marker.kind}-${marker.sourceObservedAt}-${index}`} type="button"
                className="shift-marker" data-kind={marker.kind} style={{ left: `${left}%` }}
                onClick={() => onSeek(marker.seekTo)} aria-label={marker.label}>
                <span aria-hidden="true">{marker.kind === "TOOL_CHANGE" ? "T" : "S"}</span>
              </button>
            );
          })}
          {alarms.map((alarm) => {
            const left = timelinePercent(alarm.openedAt, report.observedFrom, report.observedTo);
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
        <div className="shift-timeline-axis" aria-hidden="true">
          <span>{clock(report.observedFrom)}</span><span>{clock(report.observedTo)}</span>
        </div>
        <ul className="shift-state-legend" aria-label="상태 범례">
          {(Object.entries(STATE_LABELS) as [ShiftState, string][]).map(([state, label]) => (
            <li key={state} data-state={state}><span aria-hidden="true" />{label}</li>
          ))}
        </ul>
      </section>

      <section className="shift-alarm-summary" aria-label="확인할 알람">
        <header>
          <h3>확인할 알람</h3>
          <span>{alarms.length}건</span>
        </header>
        {alarms.length === 0 ? (
          <p>이 관측 구간에 연결된 알람이 없습니다.</p>
        ) : (
          <ul>
            {alarms.map((alarm) => (
              <li key={alarm.alarmId} data-severity={alarm.severity}>
                <button type="button" onClick={() => onSeek(alarm.openedAt)}
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
        )}
      </section>
    </section>
  );
}

function IntervalButton({ interval, report, onSeek }: {
  interval: ShiftInterval; report: ShiftOverview; onSeek: (sourceObservedAt: string) => void;
}) {
  const start = timelinePercent(interval.startedAt, report.observedFrom, report.observedTo);
  const end = timelinePercent(interval.endedAt ?? report.observedTo, report.observedFrom, report.observedTo);
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

function freshnessLabel(freshness?: Freshness, replayStatus?: ReplayStatus): string {
  if (replayStatus === "COMPLETED") return "마지막 재생 데이터";
  if (replayStatus === "PAUSED") return "선택 시점 데이터";
  if (freshness === "FRESH") return "최신";
  if (freshness === "LAGGING") return "지연됨";
  if (freshness === "STALE") return "오래됨";
  return "확인 불가";
}
