import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { App } from "./App";

const originalMatchMediaDescriptor = Object.getOwnPropertyDescriptor(window, "matchMedia");
const originalLocalStorageDescriptor = Object.getOwnPropertyDescriptor(window, "localStorage");

afterEach(() => {
  cleanup();
  restoreWindowProperty("matchMedia", originalMatchMediaDescriptor);
  restoreWindowProperty("localStorage", originalLocalStorageDescriptor);
  window.localStorage.clear();
});

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

  it("uses a workstation rail without changing primary routes", () => {
    render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    const navigation = screen.getByRole("navigation", {
      name: "ForgeSync navigation",
    });

    expect(navigation.querySelector('a[href="/"]')?.textContent).toContain("대시보드");
    expect(navigation.querySelector('a[href="/factory"]')?.textContent).toContain(
      "공장 보기",
    );
    expect(navigation.querySelector('a[href="/data-quality"]')?.textContent).toContain(
      "데이터 품질",
    );
    expect(screen.getByRole("button", { name: "화면 테마" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "운영 상황" })).toBeTruthy();
  });

  it("follows the system theme until the user chooses an override", () => {
    stubDarkSystemTheme();

    const { container } = render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-carbon-theme="g100"]')).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "화면 테마" }));

    expect(container.querySelector('[data-carbon-theme="white"]')).toBeTruthy();
    expect(window.localStorage.getItem("forgesync-theme")).toBe("LIGHT");
  });

  it("renders when matchMedia and localStorage are unavailable", () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      get: () => {
        throw new Error("matchMedia unavailable");
      },
    });
    Object.defineProperty(window, "localStorage", {
      configurable: true,
      get: () => {
        throw new Error("localStorage unavailable");
      },
    });

    const { container } = render(
      <MemoryRouter>
        <App twinSessionFactory={idleSessionFactory} />
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-carbon-theme="white"]')).toBeTruthy();
    expect(screen.getByRole("main")).toBeTruthy();
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

function stubDarkSystemTheme() {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: () => ({
      matches: true,
      media: "(prefers-color-scheme: dark)",
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
}

function restoreWindowProperty(
  property: "matchMedia" | "localStorage",
  descriptor: PropertyDescriptor | undefined,
) {
  if (descriptor === undefined) {
    Reflect.deleteProperty(window, property);
    return;
  }
  Object.defineProperty(window, property, descriptor);
}
