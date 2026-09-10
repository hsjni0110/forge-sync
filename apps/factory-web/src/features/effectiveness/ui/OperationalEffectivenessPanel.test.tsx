import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { OperationalEffectivenessReport } from "../domain/operationalEffectiveness";
import { OperationalEffectivenessPanel } from "./OperationalEffectivenessPanel";

const report: OperationalEffectivenessReport = {
  schemaVersion: "1.0.0", processingRunId: `sha256:${"a".repeat(64)}`,
  createdAt: "2026-09-10T00:00:00Z", policyVersion: "1.0.0", machineId: "Mazak01",
  replaySessionId: "123e4567-e89b-42d3-a456-426614174000", throughReplaySequence: 10,
  observedFrom: "2016-10-05T10:00:00Z", observedTo: "2016-10-05T11:00:00Z",
  utilizationProcessingRunId: `sha256:${"b".repeat(64)}`,
  cycleFeatureProcessingRunId: `sha256:${"c".repeat(64)}`,
  machiningRunProcessingRunId: `sha256:${"d".repeat(64)}`,
  inputHash: `sha256:${"e".repeat(64)}`, resultHash: `sha256:${"f".repeat(64)}`,
  availability: { status: "AVAILABLE", percent: 72.5, sourceProvenance: "OBSERVED",
    valueProvenance: "DERIVED", formula: "ACTIVE_DURATION / OBSERVED_RANGE" },
  performance: { status: "UNAVAILABLE", provenance: "UNAVAILABLE", sampleCount: 3,
    contributingFeatureSetIds: [], reason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
  throughput: { status: "UNAVAILABLE", usedTransitionCount: 0, resetCount: 0,
    unavailableObservationCount: 7, reason: "NO_USABLE_TRANSITIONS" },
  quality: { status: "UNAVAILABLE", provenance: "UNAVAILABLE",
    reason: "QUALITY_SOURCE_NOT_AVAILABLE" },
  compositeOee: { status: "UNAVAILABLE", provenance: "UNAVAILABLE",
    reason: "QUALITY_COMPONENT_UNAVAILABLE" },
};

describe("OperationalEffectivenessPanel", () => {
  it("distinguishes observed, derived, and unavailable values without an OEE number", () => {
    render(<OperationalEffectivenessPanel report={report} />);

    expect(screen.getByText("72.5%")).toBeTruthy();
    expect(screen.getByText("관측 구간에서 계산됨")).toBeTruthy();
    expect(screen.getByText("표본 부족 (3/5)")).toBeTruthy();
    expect(screen.getByText("연속된 생산량 관측 근거 부족")).toBeTruthy();
    expect(screen.getByText("품질 원천 데이터 없음")).toBeTruthy();
    expect(screen.getByText("종합 OEE 제공 불가")).toBeTruthy();
    expect(screen.queryByText(/종합 OEE.*%/)).toBeNull();
  });
});
