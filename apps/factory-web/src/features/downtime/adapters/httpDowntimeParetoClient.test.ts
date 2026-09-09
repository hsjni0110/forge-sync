import { afterEach, describe, expect, it, vi } from "vitest";

import paretoFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import { HttpDowntimeParetoClient } from "./httpDowntimeParetoClient";

afterEach(() => vi.unstubAllGlobals());

describe("HttpDowntimeParetoClient", () => {
  it("builds the immutable interval-utilization-pareto chain", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response({ processingRunId: `sha256:${"a".repeat(64)}` }))
      .mockResolvedValueOnce(response({ processingRunId: `sha256:${"b".repeat(64)}` }))
      .mockResolvedValueOnce(response(paretoFixture));
    vi.stubGlobal("fetch", fetchMock);

    const report = await new HttpDowntimeParetoClient("http://api.test").analyze(
      "Mazak01",
      "00d64db8-967e-41ba-9d09-fdd087710aac",
      100,
    );

    expect(report.entries).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toEqual({
      replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac",
      throughReplaySequence: 100,
      intervalRuleVersion: "1.0.0",
    });
    expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toEqual({
      intervalProcessingRunId: `sha256:${"a".repeat(64)}`,
      calculationVersion: "1.0.0",
    });
    expect(JSON.parse(fetchMock.mock.calls[2][1].body as string)).toEqual({
      utilizationProcessingRunId: `sha256:${"b".repeat(64)}`,
      ruleVersion: "1.0.0",
    });
  });
});

function response(document: unknown): Response {
  return new Response(JSON.stringify(document), {
    status: 201,
    headers: { "Content-Type": "application/json" },
  });
}
