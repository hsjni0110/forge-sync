import { afterEach, describe, expect, it, vi } from "vitest";

import runsFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-machining-runs.json";
import toolChangesFixture from "../../../../../../tests/fixtures/tool-changes/v1/mazak01-tool-changes.json";
import downtimeFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import utilizationFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-utilization-kpis.json";
import { HttpShiftOverviewClient } from "./httpShiftOverviewClient";

const hash = (character: string) => `sha256:${character.repeat(64)}`;
const replaySessionId = "00d64db8-967e-41ba-9d09-fdd087710aac";

describe("HttpShiftOverviewClient", () => {
  afterEach(() => vi.restoreAllMocks());

  it("maps aligned projections and rejects a utilization projection outside its versioned contract", async () => {
    const intervals = {
      schemaVersion: "1.0.0", processingRunId: hash("a"), machineId: "Mazak01",
      replaySessionId, throughReplaySequence: 40, intervalRuleVersion: "1.0.0",
      observedFrom: "2016-10-05T09:00:00Z", observedTo: "2016-10-05T09:10:00Z",
      inputHash: hash("1"), inputObservationCount: 2, resultHash: hash("2"),
      createdAt: "2026-09-12T00:00:00Z", coverage: [],
      intervals: [
        { signal: "EXECUTION", value: "FEED_HOLD", startedAt: "2016-10-05T09:00:00Z",
          endedAt: "2016-10-05T09:01:00Z", durationSeconds: 60,
          startEvidence: { replaySequence: 1, sourceObservedAt: "2016-10-05T09:00:00Z", sourceEventKey: "e-1" },
          endEvidence: { replaySequence: 2, sourceObservedAt: "2016-10-05T09:01:00Z", sourceEventKey: "e-2" } },
      ],
    };
    const utilization = { ...structuredClone(utilizationFixture), replaySessionId,
      throughReplaySequence: 40, intervalProcessingRunId: hash("a"), processingRunId: hash("b") };
    const runs = { ...structuredClone(runsFixture), replaySessionId, throughReplaySequence: 42,
      processingRunId: hash("d") };
    const toolChanges = { ...structuredClone(toolChangesFixture), replaySessionId,
      throughReplaySequence: 42 };
    const invalidUtilization = { ...utilization, schemaVersion: "9.0.0" };
    const pareto = { ...structuredClone(downtimeFixture), replaySessionId, throughReplaySequence: 40,
      utilizationProcessingRunId: hash("b"), intervalProcessingRunId: hash("a") };
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      const document = url.includes("equipment-state-intervals") ? intervals
        : url.includes("utilization-kpis") ? invalidUtilization
        : url.includes("downtime-pareto") ? pareto
        : url.includes("machining-runs") ? runs
        : toolChanges;
      return new Response(JSON.stringify(document), { status: 201,
        headers: { "Content-Type": "application/json" } });
    });

    const client = new HttpShiftOverviewClient("http://api.test");
    await expect(client.load("Mazak01", replaySessionId, 42))
      .rejects.toMatchObject({ code: "VERSION_MISMATCH" });

    Object.assign(invalidUtilization, utilization);
    const overview = await client.load("Mazak01", replaySessionId, 42);

    expect(overview).toMatchObject({
      replaySessionId, throughReplaySequence: 42, availabilityPercent: 30, cuttingPercent: 40,
    });
    // The Pareto ranks every non-operating interval; UNKNOWN must not be reported as stopped.
    expect(overview.stoppedSeconds).toBe(100);
    expect(overview.unknownSeconds).toBe(60);
    expect(overview.intervals).toEqual([{
      state: "INTERRUPTED", startedAt: "2016-10-05T09:00:00Z", endedAt: "2016-10-05T09:01:00Z",
    }]);
    expect(overview.markers.some((marker) => marker.kind === "TOOL_CHANGE")).toBe(true);
  });
});
