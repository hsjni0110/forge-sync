import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinPatch, TwinSnapshot } from "../domain/twin";
import type {
  Timer,
  TwinPatchDecoder,
  TwinSocket,
  TwinSocketCallbacks,
  TwinSocketFactory,
} from "./ports";
import { TwinLiveSession } from "./TwinLiveSession";

const testTimer: Timer = {
  schedule: (callback, delayMillis) => window.setTimeout(callback, delayMillis),
  cancel: (timerId) => window.clearTimeout(timerId),
};

const testPatchDecoder: TwinPatchDecoder = {
  decode: (message) => {
    const document = JSON.parse(message) as TwinPatch;
    if (document.type !== "TWIN_PATCH") {
      throw new Error("invalid patch");
    }
    return document;
  },
};

class ControlledSocketFactory implements TwinSocketFactory {
  readonly callbacks: TwinSocketCallbacks[] = [];

  connect(_machineId: string, callbacks: TwinSocketCallbacks): TwinSocket {
    this.callbacks.push(callbacks);
    return { close: vi.fn() };
  }
}

function snapshotAt(version: number, evaluatedAt: string, ageMillis = 0) {
  const document = structuredClone(twinFixture);
  document.consistency.twinVersion = version;
  document.consistency.projectedAt = evaluatedAt;
  document.state.freshness.evaluatedAt = evaluatedAt;
  document.state.freshness.projectedAt = evaluatedAt;
  document.state.freshness.ageMillis = ageMillis;
  return document as unknown as TwinSnapshot;
}

function patch(baseVersion: number, targetVersion: number, evaluatedAt: string) {
  const snapshot = snapshotAt(targetVersion, evaluatedAt);
  return JSON.stringify({
    schemaVersion: "1.0.0",
    type: "TWIN_PATCH",
    machineId: "Mazak01",
    baseVersion,
    targetVersion,
    projectedAt: evaluatedAt,
    snapshot,
  });
}

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

describe("TwinLiveSession", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-02T01:02:04Z"));
  });

  afterEach(() => vi.useRealTimers());

  it("loads REST before subscribing and atomically applies a continuous patch", async () => {
    let resolveSnapshot: ((snapshot: ReturnType<typeof snapshotAt>) => void) | undefined;
    const snapshotReader = {
      loadSnapshot: vi.fn(
        () =>
          new Promise<ReturnType<typeof snapshotAt>>((resolve) => {
            resolveSnapshot = resolve;
          }),
      ),
    };
    const sockets = new ControlledSocketFactory();
    const session = new TwinLiveSession(
      "Mazak01",
      snapshotReader,
      sockets,
      testPatchDecoder,
      { nowMillis: () => Date.now() },
      testTimer,
    );

    session.start();
    expect(sockets.callbacks).toHaveLength(0);
    resolveSnapshot?.(snapshotAt(4, "2026-09-02T01:02:04Z"));
    await flushPromises();
    expect(sockets.callbacks).toHaveLength(1);
    sockets.callbacks[0].opened();
    sockets.callbacks[0].received(patch(4, 5, "2026-09-02T01:02:05Z"));

    expect(session.currentState().connectionStatus).toBe("LIVE");
    expect(session.currentState().snapshot?.consistency.twinVersion).toBe(5);
    session.dispose();
  });

  it("ignores duplicate patches and resynchronizes a gap before resubscribing", async () => {
    const snapshots = [
      snapshotAt(4, "2026-09-02T01:02:04Z"),
      snapshotAt(7, "2026-09-02T01:02:07Z"),
    ];
    const snapshotReader = {
      loadSnapshot: vi.fn(async () => snapshots.shift()!),
    };
    const sockets = new ControlledSocketFactory();
    const session = new TwinLiveSession(
      "Mazak01",
      snapshotReader,
      sockets,
      testPatchDecoder,
      { nowMillis: () => Date.now() },
      testTimer,
    );

    session.start();
    await flushPromises();
    sockets.callbacks[0].received(patch(3, 4, "2026-09-02T01:02:04Z"));
    expect(session.currentState().snapshot?.consistency.twinVersion).toBe(4);
    sockets.callbacks[0].received(patch(5, 6, "2026-09-02T01:02:06Z"));
    expect(session.currentState().connectionStatus).toBe("RESYNCING");
    await flushPromises();

    expect(snapshotReader.loadSnapshot).toHaveBeenCalledTimes(2);
    expect(session.currentState().snapshot?.consistency.twinVersion).toBe(7);
    expect(sockets.callbacks).toHaveLength(2);
    session.dispose();
  });

  it("preserves the snapshot on invalid patch and becomes stale while disconnected", async () => {
    const snapshotReader = {
      loadSnapshot: vi.fn(async () => snapshotAt(4, "2026-09-02T01:02:04Z")),
    };
    const sockets = new ControlledSocketFactory();
    const session = new TwinLiveSession(
      "Mazak01",
      snapshotReader,
      sockets,
      testPatchDecoder,
      { nowMillis: () => Date.now() },
      testTimer,
    );

    session.start();
    await flushPromises();
    sockets.callbacks[0].opened();
    sockets.callbacks[0].closed();
    expect(session.currentState().connectionStatus).toBe("RECONNECTING");
    await vi.advanceTimersByTimeAsync(10_001);

    expect(session.currentState().freshness).toBe("STALE");
    expect(session.currentState().snapshot?.consistency.twinVersion).toBe(4);
    expect(snapshotReader.loadSnapshot).toHaveBeenCalledTimes(2);
    session.dispose();
  });

  it("does not damage the current snapshot while an invalid patch is resynchronized", async () => {
    let loadCount = 0;
    const snapshotReader = {
      loadSnapshot: vi.fn(() => {
        loadCount += 1;
        if (loadCount === 1) {
          return Promise.resolve(snapshotAt(4, "2026-09-02T01:02:04Z"));
        }
        return new Promise<ReturnType<typeof snapshotAt>>(() => undefined);
      }),
    };
    const sockets = new ControlledSocketFactory();
    const session = new TwinLiveSession(
      "Mazak01",
      snapshotReader,
      sockets,
      testPatchDecoder,
      { nowMillis: () => Date.now() },
      testTimer,
    );

    session.start();
    await flushPromises();
    sockets.callbacks[0].received("{}");

    expect(session.currentState().connectionStatus).toBe("RESYNCING");
    expect(session.currentState().snapshot?.consistency.twinVersion).toBe(4);
    expect(snapshotReader.loadSnapshot).toHaveBeenCalledTimes(2);
    session.dispose();
  });
});
