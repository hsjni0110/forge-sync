import type { Freshness } from "../domain/twin";

export type ReplayLifecycleStatus =
  | "PREPARING"
  | "RUNNING"
  | "PAUSED"
  | "SEEKING"
  | "COMPLETED"
  | "FAILED";

export interface TwinPresentation {
  freshnessLabel: string;
  notice?: string;
  noticeTone?: "neutral" | "danger";
  warnsAgainstRealtimeUse: boolean;
}

export function deriveTwinPresentation(
  freshness: Freshness | undefined,
  replayStatus?: ReplayLifecycleStatus,
): TwinPresentation {
  if (replayStatus === "COMPLETED") {
    return {
      freshnessLabel: "마지막 재생 데이터",
      notice: "재생이 완료되었습니다. 마지막 재생 데이터를 표시합니다.",
      noticeTone: "neutral",
      warnsAgainstRealtimeUse: false,
    };
  }
  if (replayStatus === "PAUSED") {
    return {
      freshnessLabel: "선택 시점 데이터",
      notice: "재생이 일시정지되었습니다. 선택 시점 데이터를 표시합니다.",
      noticeTone: "neutral",
      warnsAgainstRealtimeUse: false,
    };
  }
  if (freshness === "STALE") {
    return {
      freshnessLabel: "오래된 데이터",
      notice: "오래된 데이터입니다. 현재 설비의 실시간 상태로 판단하지 마세요.",
      noticeTone: "danger",
      warnsAgainstRealtimeUse: true,
    };
  }
  return {
    freshnessLabel: FRESHNESS_LABELS[freshness ?? "UNAVAILABLE"] ?? "확인할 수 없음",
    warnsAgainstRealtimeUse: false,
  };
}

const FRESHNESS_LABELS: Record<string, string> = {
  FRESH: "최신",
  LAGGING: "지연됨",
  STALE: "오래된 데이터",
  UNAVAILABLE: "확인할 수 없음",
};
