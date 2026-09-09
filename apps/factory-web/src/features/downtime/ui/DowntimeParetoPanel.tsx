import type {
  DowntimeEvidence,
  DowntimeParetoReport,
  DowntimeState,
} from "../domain/downtimePareto";

const STATE_LABELS: Record<DowntimeState, string> = {
  STOPPED: "정지",
  INTERRUPTED: "중단",
  UNKNOWN: "상태 미확인",
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
          <p className="eyebrow">관측된 정지 시간</p>
          <h2 id="downtime-pareto-title">정지 사유 Pareto</h2>
        </div>
        <strong>{formatSeconds(report.totalDowntimeSeconds)}</strong>
      </header>
      <p className="downtime-disclaimer">
        표시된 항목은 같은 시간에 관측된 근거이며 원인으로 확정하지 않습니다.
      </p>
      {report.entries.length === 0 ? (
        <p className="downtime-empty">완료된 정지 구간이 없습니다.</p>
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
                  <strong>{STATE_LABELS[entry.state]}</strong>
                  <small>
                    {formatSeconds(entry.durationSeconds)} · 누적 {entry.cumulativeRatioPercent}%
                  </small>
                </span>
                <span className="downtime-evidence-list">
                  {entry.reasonClassification === "UNCONFIRMED_REASON" ? (
                    <em>사유 미확인</em>
                  ) : (
                    entry.evidence.map((evidence) => (
                      <span key={`${evidence.kind}-${evidence.sourceEventKey}`}>
                        {evidenceLabel(evidence)}
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

function evidenceLabel(evidence: DowntimeEvidence): string {
  if (evidence.kind === "ESTOP_OVERLAP") return "비상정지 관측";
  if (evidence.kind === "MODE_CHANGE") return `운전 모드 변경 · ${evidence.value}`;
  const detail = evidence.message ?? evidence.nativeCode ?? evidence.conditionType;
  return `상태 경고${detail ? ` · ${detail}` : ""}`;
}

function formatSeconds(seconds: number): string {
  if (seconds >= 60) {
    const minutes = Math.floor(seconds / 60);
    const remainder = Math.round(seconds % 60);
    return remainder === 0 ? `${minutes}분` : `${minutes}분 ${remainder}초`;
  }
  return `${Math.round(seconds)}초`;
}
