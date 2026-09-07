import {
  lazy,
  Suspense,
  useCallback,
  useMemo,
  useState,
  useSyncExternalStore,
} from "react";

import type { TwinSessionFactory } from "../../twin/application/ports";
import type { ReplayControlClient } from "../../replay/application/ports";
import { useReplayController } from "../../replay/ui/useReplayController";
import { ProcessAnalysisPanel } from "../../process-analytics/ui/ProcessAnalysisPanel";
import type { ProcessAnalysisClient } from "../../process-analytics/application/ports";
import { ReplayControls } from "../../replay/ui/ReplayControls";
import { useReplayDrivenTwinBootstrap } from "../../replay/ui/useReplayDrivenTwinBootstrap";
import { MachineDetailView } from "../../twin/ui/MachineDetailView";
import { useTwinLiveSession } from "../../twin/ui/useTwinLiveSession";
import { MAZAK01_SCENE_BINDING, sceneBindingFromTwin } from "../adapters/defaultMachineSceneBinding";
import { mapTwinToMachineVisualState } from "../adapters/twinToMachineVisualState";
import { MachineSelectionStore } from "../application/MachineSelectionStore";
import { deriveMachineVisualPresentation } from "../domain/machineVisualPresentation";
import { SceneErrorBoundary } from "./SceneErrorBoundary";
import type {
  FactorySceneLoader,
  SceneUnavailableReason,
} from "./factorySceneContract";
import { useReducedMotionPreference } from "./useReducedMotionPreference";
import type { ToolChangeClient } from "../../tool-change/application/ports";
import type { ObservedToolpathClient } from "../../toolpath/application/ports";
import type { MachiningRun } from "../../process-analytics/domain/processAnalysis";
import { useObservedToolpath } from "../../toolpath/ui/useObservedToolpath";
import { mapObservedToolpathToScene } from "../../toolpath/domain/observedToolpath";
import { composeFunctionalTwinPresentation } from "../domain/functionalTwinPresentation";

export type FactoryViewMode = "2D" | "3D" | "SPLIT";

export function FactoryRoute({
  sessionFactory,
  replayControlClient,
  sceneLoader,
  processAnalysisClient,
  toolChangeClient,
  observedToolpathClient,
}: {
  sessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  sceneLoader: FactorySceneLoader;
  processAnalysisClient?: ProcessAnalysisClient;
  toolChangeClient?: ToolChangeClient;
  observedToolpathClient?: ObservedToolpathClient;
}) {
  const [viewMode, setViewMode] = useState<FactoryViewMode>("SPLIT");
  const [unavailableReason, setUnavailableReason] =
    useState<SceneUnavailableReason>();
  const [isAssetFallback, setIsAssetFallback] = useState(false);
  const [sceneAttempt, setSceneAttempt] = useState(0);
  const [currentRun, setCurrentRun] = useState<MachiningRun>();
  const [selectedPathRun, setSelectedPathRun] = useState<MachiningRun>();
  const { isReducedMotion, toggleReducedMotion } = useReducedMotionPreference();
  const selectionStore = useMemo(
    () => new MachineSelectionStore(MAZAK01_SCENE_BINDING.machineId),
    [],
  );
  const selectedMachineId = useSyncExternalStore(
    selectionStore.subscribe,
    selectionStore.currentSelection,
  );
  const machineId = selectedMachineId ?? MAZAK01_SCENE_BINDING.machineId;
  const replay = useReplayController(machineId, replayControlClient);
  const replayStatus = replay.session?.status;
  const createSession = useCallback(
    () => sessionFactory(machineId),
    [machineId, sessionFactory],
  );
  const { state: twinState, retryNow } = useTwinLiveSession(createSession);
  useReplayDrivenTwinBootstrap(replayStatus, twinState, retryNow);
  const visualState = useMemo(() => {
    if (!twinState.snapshot) {
      return undefined;
    }
    return {
      ...mapTwinToMachineVisualState({
      snapshot: twinState.snapshot,
      freshness: twinState.freshness ?? twinState.snapshot.state.freshness.value,
      selectedMachineId,
      visualSpindleSourceDataItemId:
        MAZAK01_SCENE_BINDING.visualSpindleSourceDataItemId,
      }),
      isReplayAdvancing: replayStatus === undefined || replayStatus === "RUNNING",
    };
  }, [replayControlClient, replayStatus, selectedMachineId, twinState.freshness, twinState.snapshot]);
  const visualPresentation = useMemo(
    () => deriveMachineVisualPresentation(visualState, isReducedMotion),
    [isReducedMotion, visualState],
  );
  const machineBinding = useMemo(
    () => sceneBindingFromTwin(visualState?.spatial),
    [visualState?.spatial],
  );
  const toolpathRequest = useMemo(() => {
    const cursor = twinState.snapshot?.replayCursor;
    const pathRun = selectedPathRun ?? currentRun;
    if (!pathRun || !cursor) return undefined;
    return {
      machineId,
      replaySessionId: cursor.replaySessionId,
      startSequence: pathRun.startSequence,
      endSequence: pathRun.endSequence ?? cursor.replaySequence,
      throughReplaySequence: cursor.replaySequence,
      resetKey: replay.authoritativeSession
        ? `${replay.authoritativeSession.revision}:${replay.authoritativeSession.speedMultiplier}`
        : undefined,
    };
  }, [currentRun, machineId, replay.authoritativeSession, selectedPathRun,
    twinState.snapshot?.replayCursor]);
  const observedPath = useObservedToolpath(observedToolpathClient, toolpathRequest);
  const sceneToolpath = useMemo(
    () => observedPath.document && machineBinding.linearAxisCoordinateMappings
      ? mapObservedToolpathToScene(observedPath.document, machineBinding.linearAxisCoordinateMappings)
      : undefined,
    [machineBinding.linearAxisCoordinateMappings, observedPath.document],
  );
  const functionalPresentation = useMemo(() => composeFunctionalTwinPresentation(
    visualState,
    currentRun,
    selectedPathRun,
    observedPath.document ? {
      replaySessionId: observedPath.document.replaySessionId,
      startSequence: observedPath.document.startSequence,
      endSequence: observedPath.document.endSequence,
      pointCount: observedPath.document.points.length,
    } : undefined,
  ), [currentRun, observedPath.document, selectedPathRun, visualState]);
  const staleAfterSeconds =
    (twinState.snapshot?.state.freshness.laggingMaxAgeMillis ?? 10_000) / 1_000;
  const LazyFactoryScene = useMemo(
    () => lazy(sceneLoader),
    [sceneLoader, sceneAttempt],
  );
  const showsScene = viewMode !== "2D";
  const showsDetail = viewMode !== "3D" || unavailableReason !== undefined;
  const retryScene = () => {
    setUnavailableReason(undefined);
    setIsAssetFallback(false);
    setSceneAttempt((attempt) => attempt + 1);
  };

  return (
    <section className={`factory-page${showsScene ? " factory-console" : ""}`}>
      <header className="factory-header">
        <div>
          <p className="eyebrow">공간 설비 보기</p>
          <h1>Factory Scene</h1>
          <p>공장 배치는 실제 NIST 위치가 아닌 시뮬레이션입니다.</p>
        </div>
        <div className="factory-controls">
          <div className="view-toggle" role="group" aria-label="공장 보기 방식">
            {VIEW_PROJECTIONS.map(({ mode, label, caption, glyph }) => (
              <button
                type="button"
                aria-pressed={viewMode === mode}
                aria-label={label}
                key={mode}
                onClick={() => {
                  setViewMode(mode);
                  if (mode === "2D") {
                    setUnavailableReason(undefined);
                    setIsAssetFallback(false);
                  }
                }}
              >
                {glyph}
                <span>{caption}</span>
              </button>
            ))}
          </div>
          <button
            type="button"
            className="motion-toggle"
            aria-pressed={isReducedMotion}
            onClick={toggleReducedMotion}
          >
            모션 줄이기
          </button>
        </div>
      </header>

      {/* One place explains the screen. Above the machine this prose cost the canvas 73px. */}
      <details className="scene-disclaimer">
        <summary>이 화면에 대하여</summary>
        <p>
          <strong>무엇을 보고 있나요?</strong> 실제 Mazak 외형이 아니라 외함, 가공실, 작업대,
          스핀들, 조작반을 구분한 범용 수직형 CNC를 단순화한 모습입니다.
        </p>
        <p>
          {staleAfterSeconds}초 동안 새 값이 없으면 안전하게 오래된 데이터로 표시합니다.
          데이터 재생이 끝났다는 뜻은 아닙니다.
        </p>
        <p>Layout provenance · {machineBinding.spatialProvenance}</p>
        {machineBinding.spatialAvailability === "FALLBACK" && (
          <p>배치 정보 사용 불가 · 기본 배치를 표시합니다.</p>
        )}
        <p>
          RPM 기반 회전은 상태 변화를 보여주는 시각 효과이며 실제 물리 회전 속도가 아닙니다.
        </p>
      </details>
      {isAssetFallback && (
        <div className="notice notice-warning" role="status">
          3D 자산을 불러오지 못해 기본 CNC 도형을 표시합니다.
        </div>
      )}

      <div className={`factory-layout factory-layout-${viewMode.toLowerCase()}`}>
        {showsScene && (
          <section className="scene-panel" aria-labelledby="factory-scene-title">
            <h2 id="factory-scene-title">3D 공장</h2>
            <div className="scene-viewport">
              {unavailableReason === undefined ? (
                <SceneErrorBoundary
                  fallback={
                    <SceneUnavailableNotice
                      reason="BUNDLE_OR_SCENE"
                      retryScene={retryScene}
                    />
                  }
                  onError={() => setUnavailableReason("BUNDLE_OR_SCENE")}
                >
                  <Suspense fallback={<SceneLoading />}>
                    <LazyFactoryScene
                      machineBinding={machineBinding}
                      visualState={visualState}
                      visualPresentation={visualPresentation}
                      functionalPresentation={functionalPresentation}
                      isReducedMotion={isReducedMotion}
                      observedToolpath={sceneToolpath}
                      selectedRunLabel={(selectedPathRun ?? currentRun)?.program
                        ? `PGM ${(selectedPathRun ?? currentRun)?.program}` : "선택한 가공"}
                      toolpathStatus={observedPath.message}
                      onSelectMachine={selectionStore.selectMachine}
                      onAssetFallback={() => setIsAssetFallback(true)}
                      onUnavailable={setUnavailableReason}
                    />
                  </Suspense>
                </SceneErrorBoundary>
              ) : (
                <SceneUnavailableNotice
                  reason={unavailableReason}
                  retryScene={retryScene}
                />
              )}
            </div>
          </section>
        )}

        {showsDetail && (
          <section className="factory-detail-panel" aria-label="2D 설비 상세">
            <MachineDetailView
              machineId={machineId}
              state={twinState}
              retryNow={retryNow}
              replayStatus={replayStatus}
              layout={viewMode === "2D" ? "FULL" : "COMPACT"}
            />
            {replayControlClient && <ProcessAnalysisPanel machineId={machineId}
              session={replay.authoritativeSession} twinState={twinState} client={processAnalysisClient}
              layout={viewMode === "2D" ? "FULL" : "COMPACT"}
              onCurrentRunChange={setCurrentRun}
              onSelectedRunChange={setSelectedPathRun}
              retryTwin={retryNow} reloadReplay={replay.reload} seek={(at) => void replay.run("SEEKING", (current) =>
                replayControlClient.seek(current.replaySessionId, current.revision, at, current.speedMultiplier))} />}
            {showsScene && <p className="section-note" role="status">{observedPath.message}</p>}
          </section>
        )}
      </div>

      {/* PRD 30 keeps the transport under the scene so the machine holds the top of the console. */}
      {replayControlClient && (
        <ReplayControls
          machineId={machineId}
          client={replayControlClient}
          snapshot={twinState.snapshot}
          freshness={twinState.freshness}
          controller={replay}
          toolChangeClient={toolChangeClient}
        />
      )}
    </section>
  );
}

/**
 * Each view is named by the projection it puts on screen, drawn the way a drawing set labels its
 * views. "2D / 3D / SPLIT" named the technology; these name what the reader will see.
 */
const VIEW_PROJECTIONS = [
  {
    mode: "2D" as const,
    label: "평면 보기",
    caption: "평면",
    glyph: (
      <svg width="26" height="16" viewBox="0 0 26 16" fill="none" aria-hidden="true">
        <rect x="6.5" y="2.5" width="13" height="11" stroke="currentColor" strokeWidth="1.2" />
        <path d="M2 8h3.5M20.5 8H24" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    mode: "3D" as const,
    label: "입체 보기",
    caption: "입체",
    glyph: (
      <svg width="26" height="16" viewBox="0 0 26 16" fill="none" aria-hidden="true">
        <path
          d="M13 1.6 21.5 6.4v5.2L13 16.4 4.5 11.6V6.4L13 1.6Z"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinejoin="round"
        />
        <path d="M13 1.6v5.2M13 6.8l8.5-.4M13 6.8 4.5 6.4" stroke="currentColor" strokeWidth="1.2" />
      </svg>
    ),
  },
  {
    mode: "SPLIT" as const,
    label: "평면과 입체 함께 보기",
    caption: "함께",
    glyph: (
      <svg width="26" height="16" viewBox="0 0 26 16" fill="none" aria-hidden="true">
        <path
          d="M8.6 2.4 14 5.4v4.4l-5.4 3-5.4-3V5.4l5.4-3Z"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinejoin="round"
        />
        <rect x="17" y="3.4" width="6.5" height="9.2" stroke="currentColor" strokeWidth="1.2" />
      </svg>
    ),
  },
];

function SceneLoading() {
  return (
    <div className="scene-loading" role="status">
      3D 화면을 불러오는 중입니다.
    </div>
  );
}

function sceneUnavailableExplanation(reason: SceneUnavailableReason): string {
  if (reason === "WEBGL") {
    return "브라우저가 WebGL 그래픽 환경을 만들지 못했습니다.";
  }
  if (reason === "WEBGL_CONTEXT_LOST") {
    return "3D 그래픽 연결이 끊겼습니다. 그래픽 드라이버나 다른 프로그램이 자원을 회수했을 수 있습니다.";
  }
  return "3D 코드를 불러오거나 실행하지 못했습니다.";
}

function SceneUnavailableNotice({
  reason,
  retryScene,
}: {
  reason: SceneUnavailableReason;
  retryScene: () => void;
}) {
  return (
    <div className="scene-unavailable" role="alert">
      <strong>3D를 사용할 수 없습니다</strong>
      <span>{sceneUnavailableExplanation(reason)}</span>
      <span>설비 상태는 2D 화면에서 계속 확인할 수 있습니다.</span>
      <button type="button" className="button-quiet" onClick={retryScene}>3D 다시 시도</button>
    </div>
  );
}
