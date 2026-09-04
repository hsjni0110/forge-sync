import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { ReplayStatus } from "../domain/replay";
import { useReplayDrivenTwinBootstrap } from "./useReplayDrivenTwinBootstrap";

afterEach(() => {
  vi.useRealTimers();
});

describe("useReplayDrivenTwinBootstrap", () => {
  it("retries a missing Twin with bounded backoff while Replay is running", async () => {
    vi.useFakeTimers();
    const retryTwin = vi.fn();
    const missingTwin: TwinLiveState = {
      connectionStatus: "UNAVAILABLE",
      failure: "NOT_FOUND",
    };
    const { rerender } = renderHook(
      ({ replayStatus, twinState }: { replayStatus?: ReplayStatus; twinState: TwinLiveState }) =>
        useReplayDrivenTwinBootstrap(replayStatus, twinState, retryTwin),
      { initialProps: { replayStatus: "RUNNING" as ReplayStatus, twinState: missingTwin } },
    );

    await act(() => vi.advanceTimersByTimeAsync(249));
    expect(retryTwin).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(retryTwin).toHaveBeenCalledTimes(1);

    rerender({ replayStatus: "RUNNING", twinState: { connectionStatus: "LOADING" } });
    rerender({ replayStatus: "RUNNING", twinState: missingTwin });
    await act(() => vi.advanceTimersByTimeAsync(500));
    expect(retryTwin).toHaveBeenCalledTimes(2);
  });

  it("does not retry a terminal 404 without a running Replay", async () => {
    vi.useFakeTimers();
    const retryTwin = vi.fn();
    renderHook(() =>
      useReplayDrivenTwinBootstrap(
        undefined,
        { connectionStatus: "UNAVAILABLE", failure: "NOT_FOUND" },
        retryTwin,
      ),
    );

    await act(() => vi.runAllTimersAsync());
    expect(retryTwin).not.toHaveBeenCalled();
  });

  it("stops after the bounded bootstrap retry schedule", async () => {
    vi.useFakeTimers();
    const retryTwin = vi.fn();
    const missingTwin: TwinLiveState = {
      connectionStatus: "UNAVAILABLE",
      failure: "NOT_FOUND",
    };
    const { rerender } = renderHook(
      ({ twinState }: { twinState: TwinLiveState }) =>
        useReplayDrivenTwinBootstrap("RUNNING", twinState, retryTwin),
      { initialProps: { twinState: missingTwin } },
    );

    for (const delayMillis of [250, 500, 1_000, 2_000, 4_000]) {
      await act(() => vi.advanceTimersByTimeAsync(delayMillis));
      rerender({ twinState: { connectionStatus: "LOADING" } });
      rerender({ twinState: missingTwin });
    }
    await act(() => vi.runAllTimersAsync());

    expect(retryTwin).toHaveBeenCalledTimes(5);
  });
});
