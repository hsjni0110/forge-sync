import { describe, expect, it, vi } from "vitest";
import type { ToolLoadTrendReport } from "../domain/toolLoadTrend";
import { HttpToolLoadTrendClient } from "./httpToolLoadTrendClient";

const PROCESSING_RUN_ID = `sha256:${"a".repeat(64)}`;

describe("HttpToolLoadTrendClient", () => {
  it("loads the exact machine and processing scope with the versioned media type", async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify(report()), { status: 200 }));
    const client = new HttpToolLoadTrendClient("http://api.example", fetcher as typeof fetch);

    const result = await client.find("Mazak01", PROCESSING_RUN_ID);

    expect(result.provenance.origin).toBe("DERIVED");
    expect(fetcher).toHaveBeenCalledWith(
      expect.stringContaining(`machiningRunProcessingRunId=sha256%3A${"a".repeat(64)}`),
      expect.objectContaining({
        headers: { Accept: "application/vnd.forgesync.tool-load-trends.v1+json" },
      }),
    );
  });

  it("rejects physical wear fields and a different machine identity", async () => {
    const invalid = { ...report(), machineId: "OtherMachine", wearPercent: 99 };
    const fetcher = vi.fn(async () => new Response(JSON.stringify(invalid), { status: 200 }));

    await expect(
      new HttpToolLoadTrendClient("", fetcher as typeof fetch).find("Mazak01", PROCESSING_RUN_ID),
    ).rejects.toThrow(/contract/);
  });
});

function report(): ToolLoadTrendReport {
  return {
    schemaVersion: "1.0.0",
    policyVersion: "1.0.0",
    machineId: "Mazak01",
    replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac",
    throughReplaySequence: 42,
    machiningRunProcessingRunId: PROCESSING_RUN_ID,
    policy: {
      minimumRawSamplesPerPoint: 3,
      minimumTrendPoints: 5,
      baselinePointCount: 3,
      minimumCoverageRatio: 0.8,
      pointFormula: "median",
      coverageFormula: "eligible observed points / observed candidate points",
      deviationFormula: "relative difference",
      slopeFormula: "OLS",
    },
    provenance: {
      origin: "DERIVED",
      sourceKind: "REAL",
      provider: "NIST",
      sourceSetId: "nist-mazak01-20161005",
    },
    groups: [],
  };
}
