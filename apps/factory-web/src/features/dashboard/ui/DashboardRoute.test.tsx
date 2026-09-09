import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { TwinSnapshot } from "../../twin/domain/twin";
import type { ReplayControlClient } from "../../replay/application/ports";
import type { ReplaySessionState } from "../../replay/domain/replay";
import type { DowntimeParetoClient } from "../../downtime/application/ports";
import paretoFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import type { DowntimeParetoReport } from "../../downtime/domain/downtimePareto";
import { DashboardRoute } from "./DashboardRoute";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

afterEach(cleanup);

function sessionFactoryFor(state: TwinLiveState): TwinSessionFactory {
  return () => ({
    start: () => undefined,
    dispose: () => undefined,
    retryNow: () => undefined,
    currentState: () => state,
    subscribe: (listener) => {
      listener(state);
      return () => undefined;
    },
  });
}

function renderDashboard(
  state: TwinLiveState,
  replayControlClient?: ReplayControlClient,
  downtimeParetoClient?: DowntimeParetoClient,
) {
  render(
    <MemoryRouter>
      <DashboardRoute
        twinSessionFactory={sessionFactoryFor(state)}
        replayControlClient={replayControlClient}
        downtimeParetoClient={downtimeParetoClient}
      />
    </MemoryRouter>,
  );
}

describe("DashboardRoute", () => {
  it("shows a loading placeholder before the first snapshot", () => {
    renderDashboard({ connectionStatus: "LOADING" });

    expect(screen.getByRole("heading", { name: "ForgeSync" })).toBeTruthy();
    expect(screen.getByText(/설비 상태를 불러오는 중입니다/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "운영 뷰 열기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });

  it("summarizes the live twin state as KPI tiles", () => {
    renderDashboard({ connectionStatus: "LIVE", snapshot, freshness: "FRESH" });

    const execution = screen.getByText("가동 상태").closest(".metric-card") as HTMLElement;
    expect(within(execution).getByText("가동 중")).toBeTruthy();
    const spindle = screen.getByText("주축 속도").closest(".metric-card") as HTMLElement;
    expect(within(spindle).getByText("49 rpm")).toBeTruthy();
    expect(screen.getByText(/트윈 데이터 버전/)).toBeTruthy();
  });

  it("warns when the twin data is stale", () => {
    renderDashboard({ connectionStatus: "RECONNECTING", snapshot, freshness: "STALE" });

    expect(screen.getByRole("alert").textContent).toMatch(/실시간 상태로 판단하지 마세요/);
  });

  it("presents a completed replay instead of a realtime stale warning", async () => {
    const completed: ReplaySessionState = {
      schemaVersion: "1.0.0",
      replaySessionId: "10000000-0000-4000-8000-000000000001",
      machineId: "Mazak01",
      sourceSetId: "nist-mazak01-20161005",
      status: "COMPLETED",
      speedMultiplier: 10,
      revision: 2,
      sourceRange: {
        startsAt: "2016-10-05T05:27:55.740Z",
        endsAt: "2016-10-05T19:15:07.025Z",
      },
    };
    const client: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(completed),
      start: vi.fn(), pause: vi.fn(), resume: vi.fn(), changeSpeed: vi.fn(), seek: vi.fn(),
    };
    renderDashboard(
      { connectionStatus: "LIVE", snapshot, freshness: "STALE" },
      client,
    );

    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/재생이 완료/));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getAllByText("마지막 재생 데이터").length).toBeGreaterThan(0);
  });

  it("seeks the replay to the selected downtime interval", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0",
      replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01",
      sourceSetId: "nist-mazak01-20161005",
      status: "PAUSED",
      speedMultiplier: 10,
      revision: 7,
      sourceRange: {
        startsAt: "2016-10-05T05:27:55.740Z",
        endsAt: "2016-10-05T19:15:07.025Z",
      },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused),
      start: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      changeSpeed: vi.fn(),
      seek: vi.fn().mockResolvedValue(paused),
    };
    const paretoClient: DowntimeParetoClient = {
      analyze: vi.fn().mockResolvedValue({
        ...paretoFixture,
        replaySessionId: snapshot.replayCursor.replaySessionId,
        throughReplaySequence: snapshot.replayCursor.replaySequence,
      } as DowntimeParetoReport),
    };
    renderDashboard(
      { connectionStatus: "LIVE", snapshot, freshness: "FRESH" },
      replayClient,
      paretoClient,
    );

    fireEvent.click(await screen.findByRole("button", { name: /1위.*정지/ }));

    await waitFor(() =>
      expect(replayClient.seek).toHaveBeenCalledWith(
        paused.replaySessionId,
        7,
        "2016-10-05T09:00:00Z",
        10,
      ),
    );
  });
});
