import type { TwinLiveState } from "../application/TwinLiveSession";
import { effectiveConsistency } from "../domain/freshness";
import {
  deriveTwinPresentation,
  type ReplayLifecycleStatus,
} from "../application/twinPresentationPolicy";

interface TwinConnectionStatusViewProps {
  state: TwinLiveState;
  layout?: "FULL" | "COMPACT";
  replayStatus?: ReplayLifecycleStatus;
}

export function TwinConnectionStatus({
  state,
  layout = "FULL",
  replayStatus,
}: TwinConnectionStatusViewProps) {
  const consistency =
    state.snapshot && state.freshness
      ? effectiveConsistency(state.snapshot, state.freshness)
      : state.snapshot?.consistency.status;
  const presentation = deriveTwinPresentation(state.freshness, replayStatus);
  const isHistoricalReplay = replayStatus === "COMPLETED" || replayStatus === "PAUSED";
  return (
    <section
      className={`status-strip${layout === "COMPACT" ? " status-strip-compact" : ""}`}
      aria-label="트윈 연결 상태"
      aria-live="polite"
    >
      <StatusValue
        label="데이터 경로"
        value={isHistoricalReplay ? "REPLAY_CONNECTED" : state.connectionStatus}
      />
      {layout === "FULL" && (
        <StatusValue
          label="데이터 일치"
          value={isHistoricalReplay && consistency === "STALE" ? "REPLAY_SNAPSHOT" : consistency ?? "UNAVAILABLE"}
        />
      )}
      <StatusValue
        label="데이터 시점"
        value={isHistoricalReplay ? "REPLAY_SNAPSHOT" : state.freshness ?? "UNAVAILABLE"}
        labelOverride={presentation.freshnessLabel}
      />
    </section>
  );
}

function StatusValue({
  label,
  value,
  labelOverride,
}: {
  label: string;
  value: string;
  labelOverride?: string;
}) {
  return (
    <div className="status-value">
      <span>{label}</span>
      <strong className="status-badge" data-status={value.toLowerCase()}>
        {labelOverride ?? STATUS_LABELS[value] ?? value}
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
  REPLAY_CONNECTED: "Replay 연결됨",
  REPLAY_SNAPSHOT: "재생 기준 데이터",
};
