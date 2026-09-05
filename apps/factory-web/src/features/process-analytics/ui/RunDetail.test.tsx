import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { MachiningRun } from "../domain/processAnalysis";
import { RunDetail } from "./RunDetail";

afterEach(cleanup);

const run: MachiningRun = {
  id: "run", status: "COMPLETED", startedAt: "2016-10-05T09:18:30Z",
  endedAt: "2016-10-05T09:19:00Z", startSequence: 1, endSequence: 2,
  confidence: "HIGH", reasons: ["EXECUTION_START_CONFIRMED"], evidence: [],
  assessment: { status: "AVAILABLE", classification: "HIGH_DEVIATION", score: 4,
    reasons: [{ feature: "durationSeconds", target: 30, median: 10, difference: 20,
      percentage: 200, direction: "HIGHER", sampleCount: 5, code: "BASELINE_IQR_DISTANCE" }], evidence: [] },
};

describe("readable process evidence", () => {
  it("explains confidence and a large duration difference without declaring a fault", () => {
    render(<RunDetail run={run} />);
    expect(screen.getByText(/신뢰도 높음/)).toBeTruthy();
    expect(screen.getByText(/실행 시작 관측 확인/)).toBeTruthy();
    expect(screen.getByText(/큰 차이/)).toBeTruthy();
    expect(screen.getByText(/가공 구간 \(초\): 관측 기반 값 30, 기준선 중앙값 10, 차이 20/)).toBeTruthy();
    expect(screen.getByText(/고장 판정이 아닙니다/)).toBeTruthy();
  });

  it("does not invent a percentage for a zero baseline or hide an unknown reason", () => {
    render(<RunDetail run={{ ...run, reasons: ["FUTURE_REASON"], assessment: {
      ...run.assessment!, reasons: [{ ...run.assessment!.reasons[0], median: 0,
        percentage: null, code: "ZERO_IQR_DEVIATION" }],
    } }} />);
    expect(screen.getByText(/FUTURE_REASON/)).toBeTruthy();
    expect(screen.getByText(/백분율 차이는 계산할 수 없음/)).toBeTruthy();
    expect(screen.getByText(/이전 값들의 산포가 0이므로 절대 차이로 비교/)).toBeTruthy();
  });
});
