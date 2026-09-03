import { lazy, Suspense, useMemo, useState } from "react";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { MachineDetailPanel } from "../../twin/ui/MachineDetailRoute";
import defaultManifestDocument from "../adapters/defaultFactoryAssetManifest.json";
import { decodeFactoryAssetManifest } from "../adapters/factoryAssetManifest";
import { SceneErrorBoundary } from "./SceneErrorBoundary";
import type {
  FactorySceneLoader,
  SceneUnavailableReason,
} from "./factorySceneContract";

export type FactoryViewMode = "2D" | "3D" | "SPLIT";

const defaultAsset = decodeFactoryAssetManifest(defaultManifestDocument).assets[0];

export function FactoryRoute({
  sessionFactory,
  sceneLoader,
}: {
  sessionFactory: TwinSessionFactory;
  sceneLoader: FactorySceneLoader;
}) {
  const [viewMode, setViewMode] = useState<FactoryViewMode>("SPLIT");
  const [unavailableReason, setUnavailableReason] =
    useState<SceneUnavailableReason>();
  const [isAssetFallback, setIsAssetFallback] = useState(false);
  const [sceneAttempt, setSceneAttempt] = useState(0);
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
    <section className="factory-page">
      <header className="factory-header">
        <div>
          <p className="eyebrow">공간 설비 보기</p>
          <h1>Factory Scene</h1>
          <p>공장 배치는 실제 NIST 위치가 아닌 시뮬레이션입니다.</p>
        </div>
        <div className="view-toggle" role="group" aria-label="공장 보기 방식">
          {(["2D", "3D", "SPLIT"] as const).map((mode) => (
            <button
              type="button"
              aria-pressed={viewMode === mode}
              key={mode}
              onClick={() => {
                setViewMode(mode);
                if (mode === "2D") {
                  setUnavailableReason(undefined);
                  setIsAssetFallback(false);
                }
              }}
            >
              {mode}
            </button>
          ))}
        </div>
      </header>

      <p className="layout-provenance">Layout provenance · SIMULATED_LAYOUT</p>
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
                      asset={defaultAsset}
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
            <MachineDetailPanel
              machineId="Mazak01"
              sessionFactory={sessionFactory}
              layout={viewMode === "2D" ? "FULL" : "COMPACT"}
            />
          </section>
        )}
      </div>
    </section>
  );
}

function SceneLoading() {
  return (
    <div className="scene-loading" role="status">
      3D 화면을 불러오는 중입니다.
    </div>
  );
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
      <span>
        {reason === "WEBGL"
          ? "브라우저가 WebGL 그래픽 환경을 만들지 못했습니다."
          : "3D 코드를 불러오거나 실행하지 못했습니다."}
      </span>
      <span>설비 상태는 2D 화면에서 계속 확인할 수 있습니다.</span>
      <button type="button" onClick={retryScene}>3D 다시 시도</button>
    </div>
  );
}
