import { describe, expect, it, vi } from "vitest";

import type { ObservedProductionContext } from "../domain/observedProductionContext";
import { HttpObservedProductionContextClient } from "./httpObservedProductionContextClient";

const PROCESSING_RUN_ID = `sha256:${"a".repeat(64)}`;

function productionContext(): ObservedProductionContext {
  return {
    schemaVersion: "1.0.0",
    ruleVersion: "1.0.0",
    machineId: "Mazak01",
    replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac",
    throughReplaySequence: 42,
    machiningRunProcessingRunId: PROCESSING_RUN_ID,
    programIntervals: [],
    programSummaries: [],
    unassignedRunCount: 0,
    partCount: {
      status: "UNAVAILABLE",
      usedTransitionCount: 0,
      resetCount: 0,
      unavailableObservationCount: 1,
      reason: "INSUFFICIENT_EVIDENCE",
      associations: [],
    },
    productionResultStatus: "NOT_OBSERVED",
  };
}

describe("HttpObservedProductionContextClient", () => {
  it("loads the requested machine and processing scope using the versioned media type", async () => {
    const fetcher = vi.fn(async () =>
      new Response(JSON.stringify(productionContext()), { status: 200 }),
    );
    const client = new HttpObservedProductionContextClient("http://api.example", fetcher as typeof fetch);

    const context = await client.find("Mazak01", PROCESSING_RUN_ID);

    expect(context.machineId).toBe("Mazak01");
    expect(fetcher).toHaveBeenCalledWith(
      expect.stringContaining(`machiningRunProcessingRunId=sha256%3A${"a".repeat(64)}`),
      expect.objectContaining({
        headers: { Accept: "application/vnd.forgesync.observed-production-context.v1+json" },
      }),
    );
  });

  it("rejects a schema-valid response from a different machine", async () => {
    const mismatched = productionContext();
    mismatched.machineId = "OtherMachine";
    const fetcher = vi.fn(async () => new Response(JSON.stringify(mismatched), { status: 200 }));

    await expect(
      new HttpObservedProductionContextClient("", fetcher as typeof fetch).find(
        "Mazak01",
        PROCESSING_RUN_ID,
      ),
    ).rejects.toThrow(/contract/);
  });
});
