import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { ToolLoadTrendReport } from "../domain/toolLoadTrend";
import { ToolLoadTrendView } from "./ToolLoadTrendPanel";

afterEach(cleanup);

describe("ToolLoadTrendView", () => {
  it("shows channel-separated derived trend without presenting physical wear or RUL", () => {
    render(<ToolLoadTrendView report={report()} />);

    expect(screen.getByText("PGM 155 · 공구 4")).toBeTruthy();
    expect(screen.getByText("Mazak01-C · Mazak01-C_2")).toBeTruthy();
    expect(screen.getByText("기준선 12% · 최근 편차 +25%")).toBeTruthy();
    expect(screen.getByText("DERIVED · REAL:NIST")).toBeTruthy();
    expect(screen.getByText("eligible observed points / observed candidate points")).toBeTruthy();
    expect(screen.getByText(/마모량이나 잔여 수명이 아닙니다/)).toBeTruthy();
    expect(screen.getByText(/Machine FAULT나 Alarm을 만들지 않습니다/)).toBeTruthy();
  });

  it("does not show a numeric trend when sample coverage is insufficient", () => {
    const insufficient = report();
    insufficient.groups[0] = {
      ...insufficient.groups[0], status: "INSUFFICIENT_COVERAGE",
      reasons: ["COVERAGE_BELOW_0_8"], coverageRatio: 0.5,
      latestDeviationPercent: undefined, slopePercentPerPoint: undefined,
    };
    render(<ToolLoadTrendView report={insufficient} />);

    expect(screen.getByText("추세 계산 안 함 · coverage 50%")).toBeTruthy();
    expect(screen.queryByText(/최근 편차/)).toBeNull();
  });
});

function report(): ToolLoadTrendReport {
  const evidence = {
    replaySequence: 1, sourceObservedAt: "2016-10-05T10:00:00Z",
    sourceEventKey: "event-1", rawRecordId: "raw-1",
    sourceDataItemId: "Mazak01-C_2", mappingVersion: "2.3.0",
  };
  return {
    schemaVersion: "1.0.0", policyVersion: "1.0.0", machineId: "Mazak01",
    replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac", throughReplaySequence: 42,
    machiningRunProcessingRunId: `sha256:${"a".repeat(64)}`,
    policy: { minimumRawSamplesPerPoint: 3, minimumTrendPoints: 5,
      baselinePointCount: 3, minimumCoverageRatio: 0.8,
      pointFormula: "median", coverageFormula: "eligible observed points / observed candidate points",
      deviationFormula: "relative difference", slopeFormula: "OLS" },
    provenance: { origin: "DERIVED", sourceKind: "REAL", provider: "NIST",
      sourceSetId: "nist-mazak01-20161005" },
    groups: [{ programName: "155", toolNumber: 4, componentId: "Mazak01-C",
      sourceDataItemId: "Mazak01-C_2", unit: "PERCENT", status: "AVAILABLE", reasons: [],
      candidatePointCount: 6, eligiblePointCount: 6, coverageRatio: 1,
      baselineMedianLoad: 12, latestDeviationPercent: 25, slopePercentPerPoint: 3,
      points: [{ machiningRunId: `sha256:${"b".repeat(64)}`, status: "AVAILABLE",
        availableSampleCount: 3, totalObservationCount: 3, medianLoad: 15,
        deviationPercent: 25, firstEvidence: evidence, lastEvidence: evidence }] }],
  };
}
