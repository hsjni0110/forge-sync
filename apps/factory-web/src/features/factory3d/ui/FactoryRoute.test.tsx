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
    const sessionFactory = vi.fn(createSession);
    renderFactory(sceneLoader, sessionFactory);

    expect(screen.getByRole("button", { name: "SPLIT" }).getAttribute("aria-pressed")).toBe(
      "true",
    );
    expect(await screen.findByTestId("healthy-scene")).toBeTruthy();
    expect(screen.getByText("3D Mazak01 · Twin v4 · selected")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByText("v4")).toBeTruthy();
    expect(screen.getByText(/SIMULATED_LAYOUT/)).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "2D" }));
    expect(screen.queryByRole("heading", { name: "3D 공장" })).toBeNull();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "데이터 품질" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "데이터 출처" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "3D" }));
    expect(await screen.findByRole("heading", { name: "3D 공장" })).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Mazak01" })).toBeNull();
    expect(sessionFactory).toHaveBeenCalledTimes(1);
  });

  it("moves the 2D panel and 3D visual state to the same patched Twin version", async () => {
    let publishState: ((state: ReturnType<TwinSession["currentState"]>) => void) | undefined;
    const sessionFactory: TwinSessionFactory = () => {
      let currentState = {
        connectionStatus: "LIVE" as const,
        snapshot,
        freshness: "FRESH" as const,
      };
      return {
        start: vi.fn(),
        subscribe: (listener) => {
          publishState = (state) => {
            currentState = state as typeof currentState;
            listener(state);
          };
          listener(currentState);
          return () => undefined;
        },
        currentState: () => currentState,
        retryNow: vi.fn(),
        dispose: vi.fn(),
      };
    };
    renderFactory(async () => ({ default: HealthyScene }), sessionFactory);
    expect(await screen.findByText("3D Mazak01 · Twin v4 · selected")).toBeTruthy();

    const patchedSnapshot = structuredClone(snapshot);
    patchedSnapshot.consistency.twinVersion = 5;
    publishState?.({
      connectionStatus: "LIVE",
      snapshot: patchedSnapshot,
      freshness: "FRESH",
    });

    expect(await screen.findByText("3D Mazak01 · Twin v5 · selected")).toBeTruthy();
    expect(screen.getByText("v5")).toBeTruthy();
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

function renderFactory(
  sceneLoader: Parameters<typeof FactoryRoute>[0]["sceneLoader"],
  sessionFactory: TwinSessionFactory = createSession,
) {
  render(
    <MemoryRouter>
      <FactoryRoute sessionFactory={sessionFactory} sceneLoader={sceneLoader} />
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

function HealthyScene({ visualState, onSelectMachine }: FactorySceneProps) {
  return (
    <button
      type="button"
      data-testid="healthy-scene"
      onClick={() => onSelectMachine("Mazak01")}
    >
      3D {visualState?.machineId} · Twin v{visualState?.twinVersion} ·{" "}
      {visualState?.selected ? "selected" : "not selected"}
    </button>
  );
}

function WebGlFailureScene({ onUnavailable }: FactorySceneProps) {
  useEffect(() => onUnavailable("WEBGL"), [onUnavailable]);
  return <div>WebGL unavailable</div>;
}
