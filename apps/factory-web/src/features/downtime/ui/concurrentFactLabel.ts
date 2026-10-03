import type { DowntimeEvidence } from "../domain/downtimePareto";

// Controller modes are source values, not ForgeSync enums; unknown ones are shown as received.
const MODE_LABELS: Record<string, string> = {
  AUTOMATIC: "자동 모드",
  SEMI_AUTOMATIC: "반자동 모드",
  MANUAL: "수동 모드",
  MANUAL_DATA_INPUT: "수동 입력 모드",
};

export function controllerModeLabel(value: string): string {
  return MODE_LABELS[value] ?? `운전 모드 ${value}`;
}

/** Names what was recorded at the same time, never why the machine stopped. */
export function concurrentFactLabel(evidence: DowntimeEvidence): string {
  if (evidence.kind === "ESTOP_OVERLAP") return "비상정지 기록";
  if (evidence.kind === "MODE_CHANGE") {
    if (evidence.value === "UNKNOWN") return "운전 모드 기록 없음";
    return `${controllerModeLabel(evidence.value)}로 바뀜`;
  }
  const level = evidence.level === "FAULT" ? "고장 신호" : "경고";
  const code = evidence.nativeCode ?? evidence.conditionType;
  return [code ? `${level} ${code}` : level, evidence.message].filter(Boolean).join(" · ");
}
