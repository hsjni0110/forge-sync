import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

afterEach(cleanup);

describe("ReplayControls", () => {
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
