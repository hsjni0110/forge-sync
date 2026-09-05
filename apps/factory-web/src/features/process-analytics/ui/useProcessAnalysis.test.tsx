import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../../twin/domain/twin";
import type { ReplaySessionState } from "../../replay/domain/replay";
import { ProcessAnalysisError, type ProcessAnalysisClient } from "../application/ports";
import type { RunAnalysis } from "../domain/processAnalysis";
import { useProcessAnalysis } from "./useProcessAnalysis";
import { hasMatchingWatermark } from "../domain/processAnalysis";

afterEach(() => { cleanup(); vi.useRealTimers(); });
const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;
const paused: ReplaySessionState = {
  schemaVersion: "1.0.0", machineId: "Mazak01", replaySessionId: snapshot.replayCursor.replaySessionId,
  sourceSetId: "nist-mazak01-20161005", status: "PAUSED", revision: 2, speedMultiplier: 10,
  publicationCursor: snapshot.replayCursor,
  sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
};
const emptyAnalysis: RunAnalysis = { processingId: "one", featureProcessingId: "two", assessmentProcessingId: "three", runs: [] };
function input(client: ProcessAnalysisClient) {
  return { machineId: "Mazak01", session: paused,
    twinState: { snapshot, connectionStatus: "LIVE" as const, freshness: "STALE" as const },
    client, retryTwin: vi.fn(), reloadReplay: vi.fn().mockResolvedValue(undefined) };
}

describe("cursor-bound process analysis", () => {
  it("preserves sub-millisecond cursor precision while accepting equivalent time zones", () => {
    const cursor = { ...snapshot.replayCursor, sourceObservedAt: "2016-10-05T09:00:00.123456789Z" };
    expect(hasMatchingWatermark(cursor, { ...paused, publicationCursor: {
      ...cursor, sourceObservedAt: "2016-10-05T18:00:00.123456789+09:00",
    } })).toBe(true);
    expect(hasMatchingWatermark(cursor, { ...paused, publicationCursor: {
      ...cursor, sourceObservedAt: "2016-10-05T09:00:00.123456788Z",
    } })).toBe(false);
  });
  it("rejects equal sequence with a different source time", async () => {
    const analyze = vi.fn().mockResolvedValue(emptyAnalysis);
    const props = input({ analyze });
    props.session = { ...paused, publicationCursor: { ...snapshot.replayCursor, sourceObservedAt: "2016-10-05T00:00:00Z" } };
    renderHook(() => useProcessAnalysis(props));
    await act(async () => undefined);
    expect(analyze).not.toHaveBeenCalled();
  });

  it("ignores a late response after the authoritative cursor changes", async () => {
    let finishOld: (analysis: RunAnalysis) => void = () => undefined;
    const analyze = vi.fn().mockImplementationOnce(() => new Promise<RunAnalysis>((resolve) => { finishOld = resolve; }))
      .mockResolvedValue({ ...emptyAnalysis, processingId: "new" });
    const props = input({ analyze });
    const { result, rerender } = renderHook((value) => useProcessAnalysis(value), { initialProps: props });
    const next = structuredClone(snapshot);
    next.consistency.twinVersion += 1;
    next.replayCursor.twinVersion += 1;
    rerender({ ...props, twinState: { ...props.twinState, snapshot: next } });
    await act(async () => undefined);
    expect(result.current.analysis?.processingId).toBe("new");
    await act(async () => { finishOld(emptyAnalysis); });
    expect(result.current.analysis?.processingId).toBe("new");
    expect((analyze.mock.calls[0][2] as AbortSignal).aborted).toBe(true);
  });

  it("hides analysis immediately on resume and leaves historical STALE analysis readable while paused", async () => {
    const props = input({ analyze: vi.fn().mockResolvedValue(emptyAnalysis) });
    const { result, rerender } = renderHook((value) => useProcessAnalysis(value), { initialProps: props });
    await act(async () => undefined);
    expect(result.current.analysis).toEqual(emptyAnalysis);
    rerender({ ...props, session: { ...paused, status: "RUNNING" } });
    expect(result.current.analysis).toBeUndefined();
    expect(result.current.message).toMatch(/일시정지/);
  });

  it("stops automatic resync after five attempts and permits explicit retry", async () => {
    vi.useFakeTimers();
    const analyze = vi.fn().mockRejectedValue(new ProcessAnalysisError("VERSION_MISMATCH"));
    const props = input({ analyze });
    const { result } = renderHook(() => useProcessAnalysis(props));
    await act(async () => undefined);
    for (const delay of [250, 500, 1000, 2000, 4000, 10000]) {
      await act(async () => { await vi.advanceTimersByTimeAsync(delay); });
    }
    expect(props.retryTwin).toHaveBeenCalledTimes(5);
    expect(analyze).toHaveBeenCalledTimes(6);
    expect(result.current.canRetry).toBe(true);
    analyze.mockResolvedValue(emptyAnalysis);
    await act(async () => { result.current.retry(); });
    expect(result.current.analysis).toEqual(emptyAnalysis);
  });
});
