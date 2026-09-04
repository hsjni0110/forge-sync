import { describe, expect, it } from "vitest";

import replayFixture from "../../../../../../tests/fixtures/replay/v1/running-replay-session.json";
import { decodeReplaySession } from "./httpReplayControlClient";

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
});
