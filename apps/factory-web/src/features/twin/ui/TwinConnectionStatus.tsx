import type { TwinLiveState } from "../application/TwinLiveSession";

interface TwinConnectionStatusViewProps {
  state: TwinLiveState;
}

export function TwinConnectionStatus({ state }: TwinConnectionStatusViewProps) {
  return (
    <section className="status-strip" aria-label="트윈 연결 상태" aria-live="polite">
      <StatusValue label="연결" value={state.connectionStatus} />
      <StatusValue
        label="데이터 일치"
        value={state.snapshot?.consistency.status ?? "UNAVAILABLE"}
      />
      <StatusValue label="최신 상태" value={state.freshness ?? "UNAVAILABLE"} />
    </section>
  );
}

function StatusValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="status-value">
      <span>{label}</span>
      <strong className="status-badge" data-status={value.toLowerCase()}>
        {STATUS_LABELS[value] ?? value}
      </strong>
    </div>
  );
}

const STATUS_LABELS: Record<string, string> = {
  LOADING: "불러오는 중",
  LIVE: "실시간 연결됨",
  RECONNECTING: "다시 연결 중",
  RESYNCING: "최신 상태 동기화 중",
  UNAVAILABLE: "확인할 수 없음",
  CONSISTENT: "정상",
  PARTIAL: "일부 정보 부족",
  DEGRADED: "품질 저하",
  FRESH: "최신",
  LAGGING: "지연됨",
  STALE: "오래된 데이터",
};
