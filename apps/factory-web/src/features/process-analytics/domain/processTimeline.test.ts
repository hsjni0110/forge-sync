import { describe, expect, it } from "vitest";

import type { MachiningRun } from "./processAnalysis";
import { filterAndGroupRuns, layoutTimeline } from "./processTimeline";

const SOURCE_RANGE = {
  startsAt: "2016-10-05T05:27:55.740Z",
  endsAt: "2016-10-05T19:15:07.025Z",
};

function run(index: number, options: Partial<MachiningRun> = {}): MachiningRun {
  const startedAt = new Date(Date.parse("2016-10-05T09:00:00Z") + index * 120_000).toISOString();
  return {
    id: `run-${index}`,
    status: "COMPLETED",
    program: index % 2 === 0 ? "1001" : "2002",
    startedAt,
    endedAt: new Date(Date.parse(startedAt) + (index % 3 + 1) * 10_000).toISOString(),
    startSequence: index * 2,
    endSequence: index * 2 + 1,
    confidence: "HIGH",
    reasons: [],
    evidence: [],
    assessment: {
      status: "AVAILABLE",
      classification: index % 3 === 0 ? "DEVIATING" : "NORMAL",
      reasons: [],
      evidence: [],
    },
    ...options,
  };
}

describe("process timeline policy", () => {
  it("uses the replay source range and aggregates a dense 122-run overview without overlap", () => {
    const layout = layoutTimeline(Array.from({ length: 122 }, (_, index) => run(index)), SOURCE_RANGE, 32);

    expect(layout.range).toEqual(SOURCE_RANGE);
    expect(layout.mode).toBe("AGGREGATED");
    expect(layout.items.length).toBeLessThanOrEqual(32);
    expect(layout.items.reduce((count, item) => count + item.runIds.length, 0)).toBe(122);
    expect(layout.items.every((item, index) => index === 0 || item.startPercent >= layout.items[index - 1].endPercent)).toBe(true);
    expect(layout.leadingIdlePercent).toBeGreaterThan(20);
  });

  it("groups only filtered runs by program and reports classification counts", () => {
    const result = filterAndGroupRuns(
      [run(0), run(1), run(2, { assessment: { status: "AVAILABLE", classification: "HIGH_DEVIATION", reasons: [], evidence: [] } })],
      { programs: ["1001"], classifications: ["NORMAL", "HIGH_DEVIATION"], minimumDurationSeconds: 15 },
    );

    expect(result.runs.map((item) => item.id)).toEqual(["run-2"]);
    expect(result.groups).toEqual([{
      program: "1001",
      count: 1,
      medianDurationSeconds: 30,
      totalDurationSeconds: 30,
      classifications: { NORMAL: 0, DEVIATING: 0, HIGH_DEVIATION: 1, UNAVAILABLE: 0 },
      runs: [expect.objectContaining({ id: "run-2" })],
    }]);
  });
});
