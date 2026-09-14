import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ObservedProductionContext } from "../domain/observedProductionContext";
import { ObservedProductionContextView } from "./ObservedProductionContextPanel";

describe("ObservedProductionContextView", () => {
  it("shows observed run statistics without claiming part count is a production result", () => {
    render(<ObservedProductionContextView report={report()} />);
    expect(screen.getByText("155")).toBeTruthy();
    expect(screen.getByText("2건 · 완료 근거 2건")).toBeTruthy();
    expect(screen.getByText("합계 180초 · 평균 90초 · 중앙값 90초")).toBeTruthy();
    expect(screen.getByText("이름 확인 불가")).toBeTruthy();
    expect(screen.getByText(/생산 완료나 양품·불량 결과로 확정하지 않습니다/)).toBeTruthy();
    expect(screen.getByText("ProductionResult · 원천에서 관측되지 않음")).toBeTruthy();
  });
});

function report(): ObservedProductionContext {
  return {
    schemaVersion: "1.0.0", ruleVersion: "1.0.0", machineId: "Mazak01",
    replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac", throughReplaySequence: 10,
    machiningRunProcessingRunId: `sha256:${"a".repeat(64)}`,
    programIntervals: [{ kind: "SUBPROGRAM", availability: "UNAVAILABLE",
      startedAt: "2016-10-05T10:00:00Z", startEvidence: { replaySequence: 1,
        sourceObservedAt: "2016-10-05T10:00:00Z", sourceEventKey: "event-1",
        rawRecordId: "raw-1", sourceDataItemId: "Mazak01-path_2" } }],
    programSummaries: [{ programName: "155", runCount: 2, completedRunCount: 2,
      totalDurationSeconds: 180, meanDurationSeconds: 90, medianDurationSeconds: 90,
      machiningRunIds: [`sha256:${"b".repeat(64)}`, `sha256:${"c".repeat(64)}`] }],
    unassignedRunCount: 0,
    partCount: { status: "UNAVAILABLE", usedTransitionCount: 0, resetCount: 0,
      unavailableObservationCount: 1, reason: "NO_USABLE_TRANSITIONS", associations: [] },
    productionResultStatus: "NOT_OBSERVED",
  };
}
