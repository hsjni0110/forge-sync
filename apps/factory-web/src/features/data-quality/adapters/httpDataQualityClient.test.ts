import { describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/data-quality/v1/mazak01-data-quality.json";
import type { DataQualityReport } from "../domain/dataQuality";
import { HttpDataQualityClient } from "./httpDataQualityClient";

describe("HttpDataQualityClient", () => {
  it("loads one exact replay scope using the versioned media type", async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify(fixture), { status: 200 }));
    const client = new HttpDataQualityClient("http://api.example", fetcher as typeof fetch);

    const report = await client.load("Mazak01", {
      replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac",
      throughReplaySequence: 42,
    });

    expect(report.overallGrade).toBeNull();
    expect(report.dimensions.semanticCoverage.ratio).toBeCloseTo(0.8767318153994706);
    expect(fetcher).toHaveBeenCalledWith(
      expect.stringContaining("replaySessionId=00d64db8-967e-41ba-9d09-fdd087710aac"),
      expect.objectContaining({
        headers: { Accept: "application/vnd.forgesync.data-quality.v1+json" },
      }),
    );
  });

  it("rejects a response that disguises an unevaluated dimension as perfect", async () => {
    const invalid = structuredClone(fixture) as unknown as DataQualityReport;
    invalid.replaySessionId = null;
    invalid.throughReplaySequence = null;
    invalid.dimensions.completeness.status = "MEASURED";
    invalid.dimensions.completeness.ratio = 1;
    const fetcher = vi.fn(async () => new Response(JSON.stringify(invalid), { status: 200 }));

    await expect(new HttpDataQualityClient("", fetcher as typeof fetch).load("Mazak01"))
      .rejects.toThrow(/contract/);
  });
});
