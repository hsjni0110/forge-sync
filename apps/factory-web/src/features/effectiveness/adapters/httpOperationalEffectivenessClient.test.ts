import { afterEach, describe, expect, it, vi } from "vitest";

import { HttpOperationalEffectivenessClient } from "./httpOperationalEffectivenessClient";

const hash = (character: string) => `sha256:${character.repeat(64)}`;

describe("HttpOperationalEffectivenessClient", () => {
  afterEach(() => vi.restoreAllMocks());

  it("builds immutable source projections before requesting the effectiveness report", async () => {
    const report = {
      schemaVersion: "1.0.0", processingRunId: hash("a"), createdAt: "2026-09-10T00:00:00Z",
      policyVersion: "1.0.0", machineId: "Mazak01",
      replaySessionId: "123e4567-e89b-42d3-a456-426614174000", throughReplaySequence: 20,
      observedFrom: "2016-10-05T10:00:00Z", observedTo: "2016-10-05T11:00:00Z",
      utilizationProcessingRunId: hash("b"), cycleFeatureProcessingRunId: hash("c"),
      machiningRunProcessingRunId: hash("d"), inputHash: hash("e"), resultHash: hash("f"),
      availability: { status: "AVAILABLE", percent: 60, sourceProvenance: "OBSERVED",
        valueProvenance: "DERIVED", formula: "ACTIVE_DURATION / OBSERVED_RANGE" },
      performance: { status: "UNAVAILABLE", provenance: "UNAVAILABLE", sampleCount: 0,
        contributingFeatureSetIds: [], reason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
      throughput: { status: "UNAVAILABLE", usedTransitionCount: 0, resetCount: 0,
        unavailableObservationCount: 1, reason: "NO_USABLE_TRANSITIONS" },
      quality: { status: "UNAVAILABLE", provenance: "UNAVAILABLE", reason: "QUALITY_SOURCE_NOT_AVAILABLE" },
      compositeOee: { status: "UNAVAILABLE", provenance: "UNAVAILABLE", reason: "QUALITY_COMPONENT_UNAVAILABLE" },
    };
    const responses = [{ processingRunId: hash("d") }, { featureProcessingRunId: hash("c") },
      { processingRunId: hash("9") }, { processingRunId: hash("b") }, report];
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async () =>
      new Response(JSON.stringify(responses.shift()), { status: 201,
        headers: { "Content-Type": "application/json" } }));

    const result = await new HttpOperationalEffectivenessClient("http://api.test")
      .analyze("Mazak01", report.replaySessionId, 20);

    expect(result.compositeOee.reason).toBe("QUALITY_COMPONENT_UNAVAILABLE");
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[4]?.[0]).toBe(
      "http://api.test/api/v1/machines/Mazak01/operational-effectiveness/processing-runs");
  });
});
