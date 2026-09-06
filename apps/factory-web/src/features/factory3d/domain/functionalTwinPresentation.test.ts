import { describe, expect, it } from "vitest";

import fixture from "../../../../../../tests/fixtures/factory3d/v1/functional-twin-replay.json";
import type { MachineVisualState } from "./machineVisualState";
import { composeFunctionalTwinPresentation } from "./functionalTwinPresentation";

describe("functional Twin replay presentation", () => {
  it("keeps the operational summary on one authoritative cursor and separates a past selection", () => {
    const presentation = composeFunctionalTwinPresentation(
      state({ sequence: 100, execution: "ACTIVE", rpm: 1200, isReplayAdvancing: true, stale: false }),
      { id: "current", program: "114", startSequence: 90 },
      { id: "past", program: "155", startSequence: 10, endSequence: 20 },
      { replaySessionId: "session-1", startSequence: 10, endSequence: 20, pointCount: 42 },
    );

    expect(presentation).toMatchObject({
      cursorLabel: "Replay #100 · Twin v7",
      execution: "가동 중",
      rpm: "1,200 rpm · OBSERVED",
      axes: "X 10.00 · Y -5.00 · Z 2.00 mm · OBSERVED",
      tool: "공구 13 · OBSERVED · 형상 미확인",
      currentRun: "현재 재생 가공 · PGM 114 · DERIVED",
      selectedPath: "선택 경로 · PGM 155 · 42점 · OBSERVED_PATH",
      bAxis: "B축 · 좌표 매핑 검증 전",
      cAxis: "C축 위치 · unavailable",
    });
  });

  it("keeps motion disabled for stopped, paused, and stale fixture frames", () => {
    for (const frame of fixture.frames) {
      const presentation = composeFunctionalTwinPresentation(state(frame));
      expect(presentation.isLiveMotionAllowed).toBe(frame.expectsMotion);
    }
  });
});

function state(frame: {
  sequence: number;
  execution: string;
  rpm: number;
  isReplayAdvancing: boolean;
  stale: boolean;
}): MachineVisualState {
  return {
    machineId: "Mazak01",
    twinVersion: 7,
    replayCursor: {
      replaySessionId: "session-1",
      replaySequence: frame.sequence,
      sourceObservedAt: "2016-10-05T09:00:00Z",
    },
    connectivity: frame.stale ? "STALE" : "ONLINE",
    execution: frame.execution as MachineVisualState["execution"],
    health: "NORMAL",
    rpm: frame.rpm,
    rpmSourceDataItemId: "Mazak01-C_5",
    axisPositions: [
      { axis: "X", millimeters: 10, unit: "MILLIMETER", sourceDataItemId: "Mazak01-X_1", sourceObservedAt: "2016-10-05T09:00:00Z" },
      { axis: "Y", millimeters: -5, unit: "MILLIMETER", sourceDataItemId: "Mazak01-Y_1", sourceObservedAt: "2016-10-05T09:00:00Z" },
      { axis: "Z", millimeters: 2, unit: "MILLIMETER", sourceDataItemId: "Mazak01-Z_1", sourceObservedAt: "2016-10-05T09:00:00Z" },
    ],
    bAxisAngleDegrees: 45,
    tool: "13",
    stale: frame.stale,
    selected: true,
    isReplayAdvancing: frame.isReplayAdvancing,
  };
}
