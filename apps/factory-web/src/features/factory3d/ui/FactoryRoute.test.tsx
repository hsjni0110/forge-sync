import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { ReplayControlClient } from "../../replay/application/ports";
import type { ReplaySessionState } from "../../replay/domain/replay";
import type { TwinSession, TwinSessionFactory } from "../../twin/application/ports";
import type { TwinSnapshot } from "../../twin/domain/twin";
import { FactoryRoute } from "./FactoryRoute";
import type { FactorySceneProps } from "./factorySceneContract";
import type { ProcessAnalysisClient } from "../../process-analytics/application/ports";
import type { ObservedToolpathClient } from "../../toolpath/application/ports";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

afterEach(cleanup);

describe("FactoryRoute", () => {
  it("loads only the selected machining run path at the authoritative cursor", async () => {
    const processAnalysisClient: ProcessAnalysisClient = { analyze: vi.fn().mockResolvedValue({
      processingId: "runs", featureProcessingId: "features", assessmentProcessingId: "assessments",
      runs: [{ id: "selected", status: "INTERRUPTED", program: "114",
        startedAt: "2016-10-05T09:00:00Z",
        startSequence: 1, confidence: "HIGH", reasons: [], evidence: [] }],
    }) };
    const observedToolpathClient: ObservedToolpathClient = { find: vi.fn().mockResolvedValue({
      schemaVersion: "1.0.0", machineId: "Mazak01",
      replaySessionId: snapshot.replayCursor.replaySessionId,
      startSequence: 1, endSequence: 4, availability: "AVAILABLE", classification: "OBSERVED_PATH",
      points: [
        { replaySequence: 1, sourceObservedAt: "2016-10-05T09:00:00Z",
          coordinatesMillimeters: [0, 0, 10], sourceObservations: [] },
        { replaySequence: 4, sourceObservedAt: snapshot.replayCursor.sourceObservedAt,
          coordinatesMillimeters: [10, -5, 20], sourceObservations: [] },
      ],
      observedEnvelope: { minimumMillimeters: [0, -5, 10], maximumMillimeters: [10, 0, 20] },
    }) };
    const replayControlClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue({ schemaVersion: "1.0.0", machineId: "Mazak01",
        replaySessionId: snapshot.replayCursor.replaySessionId, sourceSetId: "nist-mazak01-20161005",
        status: "PAUSED", revision: 2, speedMultiplier: 10, publicationCursor: snapshot.replayCursor,
        sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" } }),
      start: vi.fn(), pause: vi.fn(), resume: vi.fn(), changeSpeed: vi.fn(), seek: vi.fn(),
    };
    render(<MemoryRouter><FactoryRoute sessionFactory={createSession} replayControlClient={replayControlClient}
      processAnalysisClient={processAnalysisClient} observedToolpathClient={observedToolpathClient}
      sceneLoader={async () => ({ default: HealthyScene })} /></MemoryRouter>);

    expect(await screen.findByText("3D observed path · 2 points · PGM 114")).toBeTruthy();
    expect(screen.getByText("현재 재생 가공 · PGM 114 · DERIVED")).toBeTruthy();
    expect(screen.queryByText(/선택 경로 · PGM 114/)).toBeNull();
    expect(observedToolpathClient.find).toHaveBeenCalledWith(expect.objectContaining({
      replaySessionId: snapshot.replayCursor.replaySessionId,
      startSequence: 1, endSequence: 4, throughReplaySequence: 4,
    }), expect.any(AbortSignal));
  });

  it("keeps current process analysis available after WebGL fails", async () => {
    const processAnalysisClient: ProcessAnalysisClient = { analyze: vi.fn().mockResolvedValue({
      processingId: "runs", featureProcessingId: "features", assessmentProcessingId: "assessments",
      runs: [{ id: "open", status: "INTERRUPTED", program: "155", startedAt: snapshot.replayCursor.sourceObservedAt,
        startSequence: snapshot.replayCursor.replaySequence, confidence: "LOW", reasons: ["END_BOUNDARY_INCOMPLETE"], evidence: [] }],
    }) };
    const replayControlClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue({ schemaVersion: "1.0.0", machineId: "Mazak01",
        replaySessionId: snapshot.replayCursor.replaySessionId, sourceSetId: "nist-mazak01-20161005",
        status: "PAUSED", revision: 2, speedMultiplier: 10, publicationCursor: snapshot.replayCursor,
        sourceRange: { startsAt: snapshot.replayCursor.sourceObservedAt, endsAt: "2016-10-05T19:15:07.025Z" } }),
      start: vi.fn(), pause: vi.fn(), resume: vi.fn(), changeSpeed: vi.fn(), seek: vi.fn(),
    };
    render(<MemoryRouter><FactoryRoute sessionFactory={createSession} replayControlClient={replayControlClient}
      processAnalysisClient={processAnalysisClient} sceneLoader={async () => ({ default: WebGlFailureScene })} /></MemoryRouter>);
    expect(await screen.findByText("3D를 사용할 수 없습니다")).toBeTruthy();
    expect(await screen.findByText("가공 중단 · 종료 근거 미확정")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "공정 분석 상세 보기" })).toBeNull();
    expect(screen.getByText(/상단 2D 보기에서 가공 목록과 이상 근거/)).toBeTruthy();
    expect(screen.queryByRole("region", { name: "가공 목록과 상세" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "2D" }));
    expect(await screen.findByRole("region", { name: "가공 목록과 상세" })).toBeTruthy();
  });
  it("starts in SPLIT mode and switches between accessible 2D and 3D views", async () => {
    const sceneLoader = vi.fn(async () => ({ default: HealthyScene }));
    const sessionFactory = vi.fn(createSession);
    renderFactory(sceneLoader, sessionFactory);

    expect(screen.getByRole("button", { name: "SPLIT" }).getAttribute("aria-pressed")).toBe(
      "true",
    );
    expect(await screen.findByTestId("healthy-scene")).toBeTruthy();
    expect(screen.getByText("3D Mazak01 · Twin v4 · selected")).toBeTruthy();
    expect(screen.getByText("3D visual · ACTIVE · animation on")).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    expect(screen.getByText("v4")).toBeTruthy();
    expect(screen.getByText(/SIMULATED_LAYOUT/)).toBeTruthy();
    expect(screen.getByText(/실제 물리 회전 속도가 아닙니다/)).toBeTruthy();
    expect(screen.getByText(/범용 수직형 CNC를 단순화한 모습/)).toBeTruthy();
    expect(screen.getByText(/10초 동안 새 값이 없으면/)).toBeTruthy();
    expect(screen.getByText(/데이터 재생이 끝났다는 뜻은 아닙니다/)).toBeTruthy();

    const reducedMotion = screen.getByRole("button", { name: "모션 줄이기" });
    expect(reducedMotion.getAttribute("aria-pressed")).toBe("false");
    fireEvent.click(reducedMotion);
    expect(reducedMotion.getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByText("3D visual · ACTIVE · animation off")).toBeTruthy();

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

  it("retries Twin bootstrap after starting Replay from an empty factory", async () => {
    const retryNow = vi.fn();
    const missingSession: TwinSessionFactory = () => ({
      start: vi.fn(),
      subscribe: (listener) => {
        listener({ connectionStatus: "UNAVAILABLE", failure: "NOT_FOUND" });
        return () => undefined;
      },
      currentState: () => ({ connectionStatus: "UNAVAILABLE", failure: "NOT_FOUND" }),
      retryNow,
      dispose: vi.fn(),
    });
    const runningReplay: ReplaySessionState = {
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
    const replayControlClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(undefined),
      start: vi.fn().mockResolvedValue(runningReplay),
      pause: vi.fn(),
      resume: vi.fn(),
      changeSpeed: vi.fn(),
      seek: vi.fn(),
    };

    render(
      <MemoryRouter>
        <FactoryRoute
          sessionFactory={missingSession}
          replayControlClient={replayControlClient}
          sceneLoader={async () => ({ default: HealthyScene })}
        />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Replay 시작" }));

    await waitFor(() => expect(retryNow).toHaveBeenCalledOnce());
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

function HealthyScene({
  visualState,
  visualPresentation,
  onSelectMachine,
  observedToolpath,
  selectedRunLabel,
  functionalPresentation,
}: FactorySceneProps) {
  return (
    <div>
      <button
        type="button"
        data-testid="healthy-scene"
        onClick={() => onSelectMachine("Mazak01")}
      >
        3D {visualState?.machineId} · Twin v{visualState?.twinVersion} ·{" "}
        {visualState?.selected ? "selected" : "not selected"}
      </button>
      <span>
        3D visual · {visualPresentation.status} · animation{" "}
        {visualPresentation.isSpindleAnimating ? "on" : "off"}
      </span>
      {observedToolpath && <span>3D observed path · {observedToolpath.points.length} points · {selectedRunLabel}</span>}
      {functionalPresentation && <>
        <span>{functionalPresentation.currentRun}</span>
        {functionalPresentation.selectedPath && <span>{functionalPresentation.selectedPath}</span>}
      </>}
    </div>
  );
}

function WebGlFailureScene({ onUnavailable }: FactorySceneProps) {
  useEffect(() => onUnavailable("WEBGL"), [onUnavailable]);
  return <div>WebGL unavailable</div>;
}
