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
  it("renders the shift overview dashboard at the root route", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "교대조 개요" })).toBeTruthy();
    expect(screen.getByText(/전체 관측 구간의 가동 상태와 주요 손실/)).toBeTruthy();
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
    expect(screen.getByRole("link", { name: "데이터 품질" }).getAttribute("href")).toBe(
      "/data-quality",
    );
    expect(screen.getByRole("link", { name: "대시보드" }).getAttribute("aria-current")).toBe(
      "page",
    );
  });

  it("links from the dashboard to the operational factory view", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "시점 상세 보기" }).getAttribute("href")).toBe(
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
