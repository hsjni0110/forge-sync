import type { MachineVisualState } from "./machineVisualState";

export interface FunctionalRunReference {
  id: string;
  program?: string;
  startSequence: number;
  endSequence?: number;
}

export interface SelectedToolpathReference {
  replaySessionId: string;
  startSequence: number;
  endSequence: number;
  pointCount: number;
}

export interface FunctionalTwinPresentation {
  cursorLabel: string;
  execution: string;
  rpm: string;
  axes: string;
  tool: string;
  currentRun: string;
  selectedPath?: string;
  bAxis: string;
  cAxis: string;
  isLiveMotionAllowed: boolean;
}

export function composeFunctionalTwinPresentation(
  state: MachineVisualState | undefined,
  currentRun?: FunctionalRunReference,
  selectedRun?: FunctionalRunReference,
  toolpath?: SelectedToolpathReference,
): FunctionalTwinPresentation {
  const cursor = state?.replayCursor;
  const axes = new Map(state?.axisPositions?.map((position) => [position.axis, position]));
  const hasMatchingSelectedPath = selectedRun !== undefined && toolpath !== undefined
    && cursor !== undefined && selectedRun.id !== currentRun?.id
    && toolpath.replaySessionId === cursor.replaySessionId
    && toolpath.startSequence === selectedRun.startSequence
    && toolpath.endSequence <= (selectedRun.endSequence ?? cursor.replaySequence);
  const selectedPath = hasMatchingSelectedPath
      ? `선택 경로 · ${programLabel(selectedRun)} · ${toolpath.pointCount}점 · OBSERVED_PATH`
      : undefined;
  return {
    cursorLabel: cursor ? `Replay #${cursor.replaySequence} · Twin v${state.twinVersion}` : "Replay cursor unavailable",
    execution: executionLabel(state?.execution),
    rpm: state?.rpm === undefined
      ? "RPM unavailable"
      : `${new Intl.NumberFormat("ko-KR").format(state.rpm)} rpm · OBSERVED`,
    axes: (["X", "Y", "Z"] as const).every((axis) => axes.has(axis))
      ? `${(["X", "Y", "Z"] as const).map((axis) =>
        `${axis} ${axes.get(axis)!.millimeters.toFixed(2)}`).join(" · ")} mm · OBSERVED`
      : "XYZ 위치 · 일부 unavailable",
    tool: state?.tool === undefined
      ? "공구 · unavailable"
      : `공구 ${state.tool} · OBSERVED · 형상 미확인`,
    currentRun: currentRun
      ? `현재 재생 가공 · ${programLabel(currentRun)} · DERIVED`
      : "현재 재생 가공 · 없음",
    selectedPath,
    bAxis: "B축 · 좌표 매핑 검증 전",
    cAxis: "C축 위치 · unavailable",
    isLiveMotionAllowed: Boolean(
      state && !state.stale && state.connectivity === "ONLINE" && state.execution === "ACTIVE"
        && (state.rpm ?? 0) > 0 && state.isReplayAdvancing !== false,
    ),
  };
}

function programLabel(run: FunctionalRunReference): string {
  return run.program ? `PGM ${run.program}` : "프로그램 unavailable";
}

function executionLabel(execution: MachineVisualState["execution"] | undefined): string {
  return ({ ACTIVE: "가동 중", STOPPED: "정지됨", HOLD: "보류", READY: "준비",
    IDLE: "유휴", UNKNOWN: "상태 unavailable" } as const)[execution ?? "UNKNOWN"];
}
