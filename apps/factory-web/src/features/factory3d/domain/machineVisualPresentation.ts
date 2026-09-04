import type { MachineVisualState } from "./machineVisualState";

export const VISUAL_RPM_DIVISOR = 500;
export const MAX_VISUAL_ANGULAR_VELOCITY_RAD_PER_SEC = 4;

export type MachineVisualStatus =
  | "STALE"
  | "OFFLINE"
  | "FAULT"
  | "WARNING"
  | "HOLD"
  | "ACTIVE"
  | "STOPPED"
  | "IDLE"
  | "READY"
  | "UNKNOWN";

export interface MachineVisualPresentation {
  status: MachineVisualStatus;
  statusLabel: string;
  statusSymbol: string;
  materialTone: MachineVisualStatus;
  isSpindleAnimating: boolean;
  visualAngularVelocityRadPerSec: number;
  isBeaconPulsing: boolean;
}

export function deriveMachineVisualPresentation(
  visualState: MachineVisualState | undefined,
  isReducedMotion: boolean,
): MachineVisualPresentation {
  const status = classifyVisualStatus(visualState);
  const normalizedSpeed = normalizeVisualSpindleSpeed(visualState?.rpm);
  const isSpindleAnimating =
    visualState?.execution === "ACTIVE" &&
    visualState.connectivity === "ONLINE" &&
    !visualState.stale &&
    visualState.isReplayAdvancing !== false &&
    normalizedSpeed > 0 &&
    !isReducedMotion;

  return {
    status,
    ...STATUS_PRESENTATION[status],
    materialTone: status,
    isSpindleAnimating,
    visualAngularVelocityRadPerSec: isSpindleAnimating ? normalizedSpeed : 0,
    isBeaconPulsing:
      !isReducedMotion && (status === "WARNING" || status === "FAULT"),
  };
}

export function normalizeVisualSpindleSpeed(rpm: number | undefined): number {
  if (rpm === undefined || !Number.isFinite(rpm) || rpm <= 0) {
    return 0;
  }
  return Math.min(
    rpm / VISUAL_RPM_DIVISOR,
    MAX_VISUAL_ANGULAR_VELOCITY_RAD_PER_SEC,
  );
}

export function nextVisualSpindleRotation(
  currentRotationRadians: number,
  visualPresentation: MachineVisualPresentation,
  deltaSeconds: number,
): number {
  if (!visualPresentation.isSpindleAnimating) {
    return currentRotationRadians;
  }
  return (
    currentRotationRadians +
    visualPresentation.visualAngularVelocityRadPerSec * deltaSeconds
  );
}

function classifyVisualStatus(
  visualState: MachineVisualState | undefined,
): MachineVisualStatus {
  if (!visualState) {
    return "UNKNOWN";
  }
  if (visualState.stale || visualState.connectivity === "STALE") {
    return "STALE";
  }
  if (visualState.connectivity === "OFFLINE") {
    return "OFFLINE";
  }
  if (visualState.health === "FAULT") {
    return "FAULT";
  }
  if (visualState.health === "WARNING") {
    return "WARNING";
  }
  if (visualState.connectivity === "UNKNOWN") {
    return "UNKNOWN";
  }
  if (visualState.execution === "HOLD") {
    return "HOLD";
  }
  return visualState.execution;
}

const STATUS_PRESENTATION: Record<
  MachineVisualStatus,
  { statusLabel: string; statusSymbol: string }
> = {
  STALE: { statusLabel: "오래된 데이터", statusSymbol: "◷" },
  OFFLINE: { statusLabel: "오프라인", statusSymbol: "×" },
  FAULT: { statusLabel: "고장", statusSymbol: "!" },
  WARNING: { statusLabel: "주의", statusSymbol: "▲" },
  HOLD: { statusLabel: "일시 정지", statusSymbol: "Ⅱ" },
  ACTIVE: { statusLabel: "가동 중", statusSymbol: "▶" },
  STOPPED: { statusLabel: "정지됨", statusSymbol: "■" },
  IDLE: { statusLabel: "대기 중", statusSymbol: "●" },
  READY: { statusLabel: "작업 준비됨", statusSymbol: "◇" },
  UNKNOWN: { statusLabel: "확인되지 않음", statusSymbol: "?" },
};
