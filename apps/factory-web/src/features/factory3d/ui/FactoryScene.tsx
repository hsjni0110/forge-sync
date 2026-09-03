import { Canvas, useFrame } from "@react-three/fiber";
import { use, useEffect, useRef, useState, type ReactNode } from "react";

import { loadVerifiedGlbAsset } from "../adapters/verifiedGlbAsset";
import type { FactoryAsset } from "../domain/factoryAsset";
import { AssetErrorBoundary } from "./AssetErrorBoundary";
import type { FactorySceneProps } from "./factorySceneContract";
import { GenericMachinePrimitive } from "./GenericMachinePrimitive";

export default function FactoryScene({
  asset,
  onAssetFallback,
  onUnavailable,
}: FactorySceneProps) {
  const [webGlAvailability, setWebGlAvailability] = useState<
    "CHECKING" | "AVAILABLE" | "UNAVAILABLE"
  >("CHECKING");
  const [isSceneReady, setIsSceneReady] = useState(false);

  useEffect(() => {
    if (canCreateWebGl2Context()) {
      setWebGlAvailability("AVAILABLE");
      return;
    }
    setWebGlAvailability("UNAVAILABLE");
    onUnavailable("WEBGL");
  }, [onUnavailable]);

  if (webGlAvailability !== "AVAILABLE") {
    return <div className="scene-loading" role="status">3D 그래픽 환경을 확인하는 중입니다.</div>;
  }

  return (
    <div className="scene-canvas-root" aria-label="시뮬레이션 공장 3D 화면">
      <Canvas
        camera={{ position: [6, 4.5, 7], fov: 42 }}
        dpr={[1, 1.5]}
        gl={{ antialias: true, powerPreference: "default" }}
        shadows
        fallback={<CanvasUnsupported />}
      >
        <SceneReadySignal onReady={() => setIsSceneReady(true)} />
        <color attach="background" args={["#08151b"]} />
        <ambientLight intensity={1.5} />
        <directionalLight position={[4, 8, 5]} intensity={2.5} castShadow />
        <gridHelper args={[18, 18, "#315963", "#18323a"]} />
        <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
          <planeGeometry args={[18, 18]} />
          <meshStandardMaterial color="#0b2028" roughness={0.9} />
        </mesh>
        <group position={[0, 0.2, 0]} rotation={[0, -0.35, 0]}>
          <AssetErrorBoundary
            fallback={<GenericMachinePrimitive />}
            onError={onAssetFallback}
          >
            <FactoryAssetModel asset={asset} />
          </AssetErrorBoundary>
        </group>
      </Canvas>
      {isSceneReady && <span className="visually-hidden">3D 장면 준비됨</span>}
    </div>
  );
}

function FactoryAssetModel({ asset }: { asset: FactoryAsset }): ReactNode {
  if (asset.representation === "PROCEDURAL") {
    return <GenericMachinePrimitive />;
  }
  return <GlbMachine asset={asset} />;
}

function GlbMachine({ asset }: { asset: Extract<FactoryAsset, { representation: "GLB" }> }) {
  const model = use(loadVerifiedGlbAsset(asset));
  return <primitive object={model.scene} />;
}

function SceneReadySignal({ onReady }: { onReady: () => void }) {
  const hasReportedReady = useRef(false);
  useFrame(() => {
    if (!hasReportedReady.current) {
      hasReportedReady.current = true;
      onReady();
    }
  });
  return null;
}

function canCreateWebGl2Context(): boolean {
  try {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("webgl2", {
      alpha: true,
      antialias: true,
      depth: true,
      failIfMajorPerformanceCaveat: false,
      powerPreference: "default",
      premultipliedAlpha: true,
      preserveDrawingBuffer: false,
      stencil: false,
    });
    context?.getExtension("WEBGL_lose_context")?.loseContext();
    return context !== null;
  } catch {
    return false;
  }
}

function CanvasUnsupported() {
  return (
    <span>이 브라우저는 3D 캔버스를 표시할 수 없습니다.</span>
  );
}
