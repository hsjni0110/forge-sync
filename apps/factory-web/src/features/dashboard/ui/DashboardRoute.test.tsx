import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { TwinSnapshot } from "../../twin/domain/twin";
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

function renderDashboard(state: TwinLiveState) {
  render(
    <MemoryRouter>
      <DashboardRoute twinSessionFactory={sessionFactoryFor(state)} />
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
});
