import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { ReplaySessionState } from "../../replay/domain/replay";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { MachiningRun, RunAnalysis } from "../domain/processAnalysis";
import { ProcessAnalysisPanel } from "./ProcessAnalysisPanel";

vi.mock("./useProcessAnalysis", () => ({
  useProcessAnalysis: () => ({ analysis, message: "", canRetry: true, retry: vi.fn() }),
}));

function makeRun(index: number, program: string, classification: string): MachiningRun {
  const startedAt = new Date(Date.parse("2016-10-05T09:00:00Z") + index * 120_000).toISOString();
  return {
    id: `run-${index}`, status: "COMPLETED", program, startedAt,
    endedAt: new Date(Date.parse(startedAt) + 30_000).toISOString(),
    startSequence: index * 2 + 10, endSequence: index * 2 + 11,
    confidence: "HIGH", reasons: [], evidence: [],
    assessment: { status: "AVAILABLE", classification, reasons: [], evidence: [] },
  };
}

const analysis: RunAnalysis = {
  processingId: "runs", featureProcessingId: "features", assessmentProcessingId: "assessments",
  runs: Array.from({ length: 122 }, (_, index) => makeRun(index, index % 2 === 0 ? "1001" : "2002", index % 3 === 0 ? "DEVIATING" : "NORMAL")),
};
const session = {
  schemaVersion: "1.0.0", replaySessionId: "session", machineId: "Mazak01", sourceSetId: "nist",
  status: "PAUSED", speedMultiplier: 10, revision: 1,
  sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
} satisfies ReplaySessionState;
const twinState = { connectionStatus: "LIVE", freshness: "FRESH" } as TwinLiveState;

afterEach(cleanup);

describe("ProcessAnalysisPanel scale controls", () => {
  it("groups by program, shows classification counts, and explains the aggregated overview", () => {
    render(<ProcessAnalysisPanel machineId="Mazak01" session={session} twinState={twinState}
      retryTwin={vi.fn()} reloadReplay={vi.fn()} seek={vi.fn()} />);

    expect(screen.getByText(/122건을 시간 구간/)).toBeTruthy();
    expect(screen.getByText(/05:27/)).toBeTruthy();
    const group = screen.getByRole("group", { name: "프로그램 1001 요약" });
    expect(within(group).getByText("61건")).toBeTruthy();
    expect(within(group).getByText(/정상 40/)).toBeTruthy();
    expect(within(group).getByText(/차이 있음 21/)).toBeTruthy();
    expect(screen.getAllByRole("button", { name: /가공 선택/ })).toHaveLength(40);
    expect(screen.getByRole("button", { name: /더 보기 · 82건 남음/ })).toBeTruthy();
  });

  it("clears a selected run and explains when a filter excludes it", () => {
    render(<ProcessAnalysisPanel machineId="Mazak01" session={session} twinState={twinState}
      retryTwin={vi.fn()} reloadReplay={vi.fn()} seek={vi.fn()} />);

    fireEvent.click(screen.getAllByRole("button", { name: /가공 선택 · 1001/ })[0]);
    fireEvent.change(screen.getByLabelText("프로그램 필터"), { target: { value: "2002" } });

    expect(screen.getByText(/현재 필터에서 제외/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "가공 시작 시점으로 이동" })).toBeNull();
  });

  it("publishes the selected run for the 3D observed path", () => {
    const onCurrentRunChange = vi.fn();
    const onSelectedRunChange = vi.fn();
    render(<ProcessAnalysisPanel machineId="Mazak01" session={session} twinState={twinState}
      retryTwin={vi.fn()} reloadReplay={vi.fn()} seek={vi.fn()}
      onCurrentRunChange={onCurrentRunChange}
      onSelectedRunChange={onSelectedRunChange} />);

    expect(onCurrentRunChange).toHaveBeenLastCalledWith(undefined);
    expect(onSelectedRunChange).toHaveBeenLastCalledWith(undefined);

    fireEvent.click(screen.getAllByRole("button", { name: /가공 선택 · 1001/ })[0]);

    expect(onSelectedRunChange).toHaveBeenLastCalledWith(analysis.runs[0]);
  });
});
