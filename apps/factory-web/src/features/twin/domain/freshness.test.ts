import { describe, expect, it } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "./twin";
import {
  classifyFreshness,
  effectiveConnectivity,
  effectiveConsistency,
} from "./freshness";

function snapshotFixture(): TwinSnapshot {
  return structuredClone(twinFixture) as unknown as TwinSnapshot;
}

describe("browser freshness projection", () => {
  it("uses the server-owned freshness window", () => {
    const snapshot = snapshotFixture();
    snapshot.state.freshness.freshMaxAgeMillis = 5_000;
    snapshot.state.freshness.laggingMaxAgeMillis = 30_000;
    const evaluatedAt = Date.parse(snapshot.state.freshness.evaluatedAt);

    expect(classifyFreshness(snapshot, evaluatedAt + 4_001)).toBe("LAGGING");
    expect(classifyFreshness(snapshot, evaluatedAt + 29_001)).toBe("STALE");
  });

  it("makes consistency and online connectivity stale together", () => {
    const snapshot = snapshotFixture();
    snapshot.consistency.status = "CONSISTENT";

    expect(effectiveConsistency(snapshot, "STALE")).toBe("STALE");
    expect(effectiveConnectivity(snapshot, "STALE")).toBe("STALE");
  });

  it("preserves a server classification beyond a sub-millisecond boundary", () => {
    const snapshot = snapshotFixture();
    snapshot.state.freshness.value = "LAGGING";
    snapshot.state.freshness.ageMillis = snapshot.state.freshness.freshMaxAgeMillis;

    expect(
      classifyFreshness(snapshot, Date.parse(snapshot.state.freshness.evaluatedAt)),
    ).toBe("LAGGING");
  });
});
