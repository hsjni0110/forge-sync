import type { DowntimeParetoReport, DowntimeState } from "../domain/downtimePareto";
import { secondsInStates } from "../domain/downtimePareto";
import { concurrentFactLabel } from "./concurrentFactLabel";

const STATE_LABELS: Record<DowntimeState, string> = {
  STOPPED: "멈춤",
  INTERRUPTED: "작업 중단",
  UNKNOWN: "기록 없음",
};

export function DowntimeParetoPanel({
  report,
  onSelect,
}: {
  report: DowntimeParetoReport;
  onSelect: (startedAt: string) => void;
}) {
  return (
    <section className="downtime-panel" aria-labelledby="downtime-pareto-title">
      <header>
        <div>
          <p className="eyebrow">관측된 비가동 시간</p>
          <h2 id="downtime-pareto-title">주요 비가동 구간</h2>
        </div>
        <div className="downtime-total">
          <strong>{formatSeconds(report.totalDowntimeSeconds)}</strong>
          <small>
            {`멈춤 ${formatSeconds(secondsInStates(report.entries, ["STOPPED", "INTERRUPTED"]))}`}
            {` · 기록 없음 ${formatSeconds(secondsInStates(report.entries, ["UNKNOWN"]))}`}
          </small>
        </div>
      </header>
      <p className="downtime-disclaimer">
        같은 시간에 함께 기록된 사실이에요. 멈춘 원인이라고 단정하지 않아요.
      </p>
      {report.entries.length === 0 ? (
        <p className="downtime-empty">끝난 비가동 구간이 없어요.</p>
      ) : (
        <ol className="downtime-list">
          {report.entries.map((entry) => (
            <li key={`${entry.rank}-${entry.startedAt}`}>
              <button
                type="button"
                data-started-at={entry.startedAt}
                onClick={() => onSelect(entry.startedAt)}
              >
                <span className="downtime-rank">{entry.rank}위</span>
                <span className="downtime-summary">
                  <strong><DowntimeStateIcon state={entry.state} />{STATE_LABELS[entry.state]}</strong>
                  <small>
                    {formatSeconds(entry.durationSeconds)} · 누적 {entry.cumulativeRatioPercent}%
                  </small>
                </span>
                <span className="downtime-evidence-list">
                  {entry.reasonClassification === "UNCONFIRMED_REASON" ? (
                    <em>함께 기록된 사실 없음</em>
                  ) : (
                    entry.evidence.map((evidence) => (
                      <span key={`${evidence.kind}-${evidence.sourceEventKey}`}>
                        {concurrentFactLabel(evidence)}
                      </span>
                    ))
                  )}
                </span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function DowntimeStateIcon({ state }: { state: DowntimeState }) {
  if (state === "UNKNOWN") {
    return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M7.3 10.3h1.4v1.4H7.3v-1.4Zm.7-8a4 4 0 0 1 2.4 7.2c-.9.7-1.7 1-1.7 1.9H7.3c0-1.6 1.1-2.3 2.1-3A2.6 2.6 0 1 0 5.4 6H4a4 4 0 0 1 4-3.7Z" />
    </svg>;
  }
  if (state === "INTERRUPTED") {
    return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M2 2h4v12H2V2Zm8 0h4v12h-4V2Z" />
    </svg>;
  }
  return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
    <path d="M3 3h10v10H3V3Z" />
  </svg>;
}

function formatSeconds(seconds: number): string {
  if (seconds >= 60) {
    const minutes = Math.floor(seconds / 60);
    const remainder = Math.round(seconds % 60);
    return remainder === 0 ? `${minutes}분` : `${minutes}분 ${remainder}초`;
  }
  return `${Math.round(seconds)}초`;
}
