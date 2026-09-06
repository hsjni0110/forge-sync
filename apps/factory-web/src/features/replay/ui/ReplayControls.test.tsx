import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../../twin/domain/twin";
import type { ReplayControlClient } from "../application/ports";
import type { ReplaySessionState } from "../domain/replay";
import { ReplayControls } from "./ReplayControls";

const running: ReplaySessionState = {
  schemaVersion: "1.0.0",
  replaySessionId: "10000000-0000-4000-8000-000000000001",
  machineId: "Mazak01",
  sourceSetId: "nist-mazak01-20161005",
  status: "RUNNING",
  speedMultiplier: 10,
  revision: 1,
  sourceRange: {
    startsAt: "2016-10-05T05:27:55.740Z",
    endsAt: "2016-10-05T19:15:07.025Z",
  },
};

afterEach(() => { cleanup(); vi.useRealTimers(); });

describe("ReplayControls", () => {
  it("observes natural replay completion without a browser command", async () => {
    vi.useFakeTimers();
    const load = vi.fn().mockResolvedValueOnce(running).mockResolvedValue({ ...running, status: "COMPLETED" });
    render(<ReplayControls machineId="Mazak01" client={clientWith({ load })} />);
    await act(async () => undefined);
    await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
    expect(screen.getByText("재생 완료")).toBeTruthy();
  });

  it("does not publish an optimistic pause as an authoritative analysis session", async () => {
    const onSessionChange = vi.fn();
    let finishPause: (value: ReplaySessionState) => void = () => undefined;
    const pause = vi.fn(() => new Promise<ReplaySessionState>((resolve) => { finishPause = resolve; }));
    render(<ReplayControls machineId="Mazak01" client={clientWith({ pause })} onSessionChange={onSessionChange} />);
    fireEvent.click(await screen.findByRole("button", { name: "일시정지" }));
    expect(onSessionChange.mock.calls.at(-1)?.[0]?.status).not.toBe("PAUSED");
    await act(async () => { finishPause({ ...running, status: "PAUSED", revision: 2 }); });
    expect(onSessionChange.mock.calls.at(-1)?.[0]?.status).toBe("PAUSED");
  });
  it("separates source time, replay time, and Twin freshness", async () => {
    const client = clientWith({ load: vi.fn().mockResolvedValue(running) });
    render(
      <ReplayControls
        machineId="Mazak01"
        client={client}
        snapshot={structuredClone(twinFixture) as unknown as TwinSnapshot}
        freshness="FRESH"
      />,
    );

    expect(await screen.findByText("Source Time")).toBeTruthy();
    expect(screen.getByText("Replay Time")).toBeTruthy();
    expect(screen.getByText("Twin Freshness")).toBeTruthy();
    expect(screen.getByRole("slider", { name: "Replay timeline" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "일시정지" })).toBeTruthy();
  });

  it("restores authoritative state when an optimistic pause is rejected", async () => {
    const paused = { ...running, status: "PAUSED" as const, revision: 2 };
    const load = vi.fn().mockResolvedValueOnce(running).mockResolvedValueOnce(paused);
    const client = clientWith({
      load,
      pause: vi.fn().mockRejectedValue(new Error("conflict")),
    });
    render(<ReplayControls machineId="Mazak01" client={client} />);

    fireEvent.click(await screen.findByRole("button", { name: "일시정지" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "재생" })).toBeTruthy());
    expect(screen.getByRole("alert").textContent).toMatch(/권위 상태/);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("retries a rejected command once against the refreshed revision on a conflict", async () => {
    const conflict = Object.assign(new Error("revision conflict"), {
      status: 409,
      code: "REPLAY_STATE_CONFLICT",
    });
    const load = vi
      .fn()
      .mockResolvedValueOnce(running)
      .mockResolvedValueOnce({ ...running, revision: 2 });
    const pause = vi
      .fn()
      .mockRejectedValueOnce(conflict)
      .mockResolvedValueOnce({ ...running, status: "PAUSED", revision: 3 });
    render(<ReplayControls machineId="Mazak01" client={clientWith({ load, pause })} />);

    fireEvent.click(await screen.findByRole("button", { name: "일시정지" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "재생" })).toBeTruthy());
    expect(pause).toHaveBeenCalledTimes(2);
    expect(pause).toHaveBeenLastCalledWith(running.replaySessionId, 2);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("keeps reading the session briefly after a seek settles into PAUSED", async () => {
    vi.useFakeTimers();
    const onSessionChange = vi.fn();
    const load = vi
      .fn()
      .mockResolvedValueOnce(running)
      .mockResolvedValueOnce({ ...running, status: "PAUSED", revision: 2 })
      .mockResolvedValue({ ...running, status: "PAUSED", revision: 3 });
    const seek = vi.fn().mockResolvedValue({ ...running, status: "SEEKING", revision: 2 });
    render(
      <ReplayControls
        machineId="Mazak01"
        client={clientWith({ load, seek })}
        onSessionChange={onSessionChange}
      />,
    );
    await act(async () => undefined);

    fireEvent.click(screen.getByRole("button", { name: "처음으로 이동" }));
    await act(async () => undefined);
    await act(async () => { await vi.advanceTimersByTimeAsync(250); });
    await act(async () => { await vi.advanceTimersByTimeAsync(650); });

    expect(load.mock.calls.length).toBeGreaterThanOrEqual(4);
    expect(onSessionChange.mock.calls.at(-1)?.[0]?.revision).toBe(3);
  });

  it("does not offer Replay start when the current session could not be checked", async () => {
    const load = vi.fn().mockRejectedValue(new Error("service unavailable"));
    const start = vi.fn();
    render(<ReplayControls machineId="Mazak01" client={clientWith({ load, start })} />);

    expect((await screen.findByRole("alert")).textContent).toMatch(/불러오지 못했습니다/);
    expect(screen.queryByRole("button", { name: "Replay 시작" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Replay 상태 다시 확인" }));
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    expect(start).not.toHaveBeenCalled();
  });

  it("offers Replay start when the server confirms there is no session", async () => {
    render(
      <ReplayControls
        machineId="Mazak01"
        client={clientWith({ load: vi.fn().mockResolvedValue(undefined) })}
      />,
    );

    expect(
      (await screen.findByRole("button", { name: "Replay 시작" })).hasAttribute("disabled"),
    ).toBe(false);
  });
});

function clientWith(overrides: Partial<ReplayControlClient>): ReplayControlClient {
  return {
    load: vi.fn().mockResolvedValue(running),
    start: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    changeSpeed: vi.fn(),
    seek: vi.fn(),
    ...overrides,
  };
}
