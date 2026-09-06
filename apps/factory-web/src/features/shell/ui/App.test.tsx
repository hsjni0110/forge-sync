import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { App } from "./App";

afterEach(cleanup);

const idleSessionFactory: TwinSessionFactory = () => ({
  start: () => undefined,
  dispose: () => undefined,
  retryNow: () => undefined,
  currentState: () => ({ connectionStatus: "LOADING" }),
  subscribe: () => () => undefined,
});

describe("App", () => {
  it("renders the ForgeSync dashboard at the root route", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "ForgeSync" })).toBeTruthy();
    expect(screen.getByText(/현재 상태와 데이터 최신성/)).toBeTruthy();
    expect(screen.getByText(/설비 상태를 불러오는 중입니다/)).toBeTruthy();
  });

  it("exposes dashboard and factory navigation", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "대시보드" }).getAttribute("href")).toBe("/");
    expect(screen.getByRole("link", { name: "공장 보기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });

  it("links from the dashboard to the operational factory view", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "운영 뷰 열기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });

  it("shows the not-found page for an unknown route", () => {
    render(
      <MemoryRouter initialEntries={["/machines/Mazak01"]}>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", { name: "페이지를 찾을 수 없습니다" }),
    ).toBeTruthy();
  });
});
