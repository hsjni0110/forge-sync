import { afterEach, describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/toolpath/v1/mazak01-observed-toolpath.json";
import { HttpObservedToolpathClient } from "./httpObservedToolpathClient";

afterEach(() => vi.restoreAllMocks());

describe("HttpObservedToolpathClient", () => {
  it("requests the selected run and accepts the shared contract", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(fixture), { status: 200 }),
    );
    const document = await new HttpObservedToolpathClient("http://api").find({
      machineId: "Mazak01",
      replaySessionId: fixture.replaySessionId,
      startSequence: 10,
      endSequence: 12,
      throughReplaySequence: 12,
    }, new AbortController().signal);

    expect(document.points).toHaveLength(2);
    expect(fetchMock.mock.calls[0][0]).toContain("startSequence=10");
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      headers: { Accept: "application/vnd.forgesync.observed-toolpath.v1+json" },
    });
  });

  it("rejects a mismatched session instead of drawing it", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ ...fixture, replaySessionId: "other" }), { status: 200 }),
    );
    await expect(new HttpObservedToolpathClient().find({
      machineId: "Mazak01", replaySessionId: fixture.replaySessionId,
      startSequence: 10, endSequence: 12, throughReplaySequence: 12,
    }, new AbortController().signal)).rejects.toThrow(/contract mismatch/);
  });
});
