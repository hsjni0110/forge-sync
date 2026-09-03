import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSession, TwinSessionFactory } from "../../twin/application/ports";
import type { TwinSnapshot } from "../../twin/domain/twin";
import { FactoryRoute } from "./FactoryRoute";
import type { FactorySceneProps } from "./factorySceneContract";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

afterEach(cleanup);

describe("FactoryRoute", () => {
  it("starts in SPLIT mode and switches between accessible 2D and 3D views", async () => {
    const sceneLoader = vi.fn(async () => ({ default: HealthyScene }));
    renderFactory(sceneLoader);

    expect(screen.getByRole("button", { name: "SPLIT" }).getAttribute("aria-pressed")).toBe(
      "true",
    );
    expect(await screen.findByTestId("healthy-scene")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByText(/SIMULATED_LAYOUT/)).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "2D" }));
    expect(screen.queryByRole("heading", { name: "3D 공장" })).toBeNull();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "데이터 품질" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "데이터 출처" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "3D" }));
    expect(await screen.findByRole("heading", { name: "3D 공장" })).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Mazak01" })).toBeNull();
  });

  it("contains a rejected 3D bundle and restores the 2D panel", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const sceneLoader = vi
      .fn()
      .mockRejectedValueOnce(new Error("chunk unavailable"))
      .mockResolvedValue({ default: HealthyScene });
    renderFactory(sceneLoader);

    expect(await screen.findByText("3D를 사용할 수 없습니다")).toBeTruthy();
    expect(screen.getByText(/3D 코드를 불러오거나 실행하지 못했습니다/)).toBeTruthy();
    expect(await screen.findByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByText(/2D 화면에서 계속 확인할 수 있습니다/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "3D 다시 시도" }));
    expect(await screen.findByTestId("healthy-scene")).toBeTruthy();
    expect(sceneLoader).toHaveBeenCalledTimes(2);
    consoleError.mockRestore();
  });

  it("keeps the 2D panel available when the scene reports WebGL failure", async () => {
    renderFactory(async () => ({ default: WebGlFailureScene }));

    await waitFor(() =>
      expect(screen.getByText(/2D 화면에서 계속 확인할 수 있습니다/)).toBeTruthy(),
    );
    expect(screen.getByText(/WebGL 그래픽 환경을 만들지 못했습니다/)).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
  });
});

function renderFactory(sceneLoader: Parameters<typeof FactoryRoute>[0]["sceneLoader"]) {
  render(
    <MemoryRouter>
      <FactoryRoute sessionFactory={createSession} sceneLoader={sceneLoader} />
    </MemoryRouter>,
  );
}

const createSession: TwinSessionFactory = () => {
  const state = { connectionStatus: "LIVE" as const, snapshot, freshness: "FRESH" as const };
  const session: TwinSession = {
    start: vi.fn(),
    subscribe: (listener) => {
      listener(state);
      return () => undefined;
    },
    currentState: () => state,
    retryNow: vi.fn(),
    dispose: vi.fn(),
  };
  return session;
};

function HealthyScene() {
  return <div data-testid="healthy-scene">3D scene ready</div>;
}

function WebGlFailureScene({ onUnavailable }: FactorySceneProps) {
  useEffect(() => onUnavailable("WEBGL"), [onUnavailable]);
  return <div>WebGL unavailable</div>;
}
