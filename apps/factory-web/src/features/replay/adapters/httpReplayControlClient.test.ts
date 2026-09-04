import { afterEach, describe, expect, it, vi } from "vitest";

import replayFixture from "../../../../../../tests/fixtures/replay/v1/running-replay-session.json";
import {
  decodeReplaySession,
  HttpReplayControlClient,
  ReplayControlRequestError,
} from "./httpReplayControlClient";

afterEach(() => vi.unstubAllGlobals());

describe("Replay Session contract decoder", () => {
  it("accepts the shared fixture and rejects an unsupported speed", () => {
    expect(decodeReplaySession(structuredClone(replayFixture)).status).toBe("RUNNING");
    expect(() =>
      decodeReplaySession({ ...structuredClone(replayFixture), speedMultiplier: 2 }),
    ).toThrow(/contract/);
  });

  it("rejects an inverted source range", () => {
    const invalid = structuredClone(replayFixture);
    invalid.sourceRange.startsAt = "2016-10-06T00:00:00Z";
    expect(() => decodeReplaySession(invalid)).toThrow(/range/);
  });

  it("treats only the explicit missing-session problem as an absent session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: "REPLAY_SESSION_NOT_FOUND" }), {
          status: 404,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    await expect(new HttpReplayControlClient("").load("Mazak01")).resolves.toBeUndefined();
  });

  it("preserves a route 404 as a load failure instead of reporting no session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Not Found", { status: 404 })),
    );

    await expect(new HttpReplayControlClient("").load("Mazak01")).rejects.toEqual(
      expect.objectContaining<Partial<ReplayControlRequestError>>({ status: 404 }),
    );
  });
});
