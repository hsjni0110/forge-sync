import { Canvas, useFrame } from "@react-three/fiber";
import { useCallback, useEffect, useRef, useState } from "react";

import { FloatingMachineLabel } from "./FloatingMachineLabel";
import type { FactorySceneProps } from "./factorySceneContract";
import { MachineTwin } from "./MachineTwin";
import { CameraNavigationRig, type CameraCommand, type CameraCommandType } from "./CameraNavigationRig";
import type { MachineInspectionPartId, MachineTwinModel } from "./model/machineTwinModel";
import { ObservedToolpathTrail } from "./ObservedToolpathTrail";

export default function FactoryScene({
  machineBinding,
  visualState,
  visualPresentation,
  isReducedMotion = false,
  observedToolpath,
  selectedRunLabel,
  toolpathStatus,
  onSelectMachine,
  onAssetFallback,
  onUnavailable,
}: FactorySceneProps) {
  const [webGlAvailability, setWebGlAvailability] = useState<
    "CHECKING" | "AVAILABLE" | "UNAVAILABLE"
  >("CHECKING");
  const [isSceneReady, setIsSceneReady] = useState(false);
  const [model, setModel] = useState<MachineTwinModel>();
  const [selectedPartId, setSelectedPartId] = useState<MachineInspectionPartId>();
  const [hoveredPartId, setHoveredPartId] = useState<MachineInspectionPartId>();
  const [isEnclosureTransparent, setIsEnclosureTransparent] = useState(false);
  const [isToolpathVisible, setIsToolpathVisible] = useState(true);
  const [cameraCommand, setCameraCommand] = useState<CameraCommand>();
  const cameraSequence = useRef(0);
  const handleModelReady = useCallback((readyModel?: MachineTwinModel) => {
    setModel(readyModel);
  }, []);
  const issueCameraCommand = useCallback((type: CameraCommandType, partId?: MachineInspectionPartId) => {
    cameraSequence.current += 1;
    setCameraCommand({ sequence: cameraSequence.current, type, partId });
  }, []);
  const selectPart = useCallback((partId?: MachineInspectionPartId) => {
    setSelectedPartId(partId);
    if (partId) {
      onSelectMachine(machineBinding.machineId);
      issueCameraCommand("FOCUS_PART", partId);
    }
  }, [issueCameraCommand, machineBinding.machineId, onSelectMachine]);

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
      <div
        className="scene-canvas-stage"
        aria-label="3D 조작 영역"
        tabIndex={0}
        onKeyDown={(event) => {
        const command = keyboardCameraCommand(event.key);
        if (command === "CLEAR_SELECTION") {
          selectPart(undefined);
          return;
        }
        if (command) {
          event.preventDefault();
          issueCameraCommand(command);
        }
        }}
      >
        <Canvas
        camera={{ position: [6, 4.5, 7], fov: 42 }}
        dpr={[1, 1.5]}
        gl={{ antialias: true, powerPreference: "default" }}
        shadows
        fallback={<CanvasUnsupported />}
        onPointerMissed={() => selectPart(undefined)}
      >
        <SceneReadySignal onReady={() => setIsSceneReady(true)} />
        <color attach="background" args={["#10161a"]} />
        <ambientLight intensity={1.25} />
        <directionalLight position={[4, 8, 5]} intensity={2.1} castShadow />
        <pointLight position={[0, 2.4, 1.8]} intensity={3} distance={5} color="#e6dfd2" />
        <gridHelper args={[18, 18, "#2c3539", "#1b2327"]} />
        <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
          <planeGeometry args={[18, 18]} />
          <meshStandardMaterial color="#121b20" roughness={0.9} />
        </mesh>
        <MachineTwin
          binding={machineBinding}
          visualState={visualState}
          visualPresentation={visualPresentation}
          onSelectMachine={onSelectMachine}
          onAssetFallback={onAssetFallback}
          selectedPartId={selectedPartId}
          hoveredPartId={hoveredPartId}
          isEnclosureTransparent={isEnclosureTransparent}
          onSelectPart={selectPart}
          onHoverPart={setHoveredPartId}
          onModelReady={handleModelReady}
        />
        {isToolpathVisible && observedToolpath && (
          <group
            position={machineBinding.position}
            rotation={machineBinding.rotation}
            scale={machineBinding.scale}
          >
            <ObservedToolpathTrail toolpath={observedToolpath} />
          </group>
        )}
        <CameraNavigationRig
          model={model}
          command={cameraCommand}
          isReducedMotion={isReducedMotion}
        />
        </Canvas>
        <FloatingMachineLabel
          binding={machineBinding}
          visualState={visualState}
          visualPresentation={visualPresentation}
          onSelectMachine={onSelectMachine}
        />
        {isSceneReady && <span className="visually-hidden">3D 장면 준비됨</span>}
      </div>
      <MachineInspectionControls
        model={model}
        selectedPartId={selectedPartId}
        isEnclosureTransparent={isEnclosureTransparent}
        issueCameraCommand={issueCameraCommand}
        selectPart={selectPart}
        toggleEnclosure={() => setIsEnclosureTransparent((current) => !current)}
        bAxisStatus={bAxisStatus(machineBinding, visualState)}
        linearAxisStatus={linearAxisStatus(machineBinding, visualState, model)}
        toolStatus={toolStatus(visualState)}
        observedToolpath={observedToolpath}
        selectedRunLabel={selectedRunLabel}
        toolpathStatus={toolpathStatus}
        isToolpathVisible={isToolpathVisible}
        toggleToolpath={() => setIsToolpathVisible((current) => !current)}
      />
    </div>
  );
}

function MachineInspectionControls({
  model,
  selectedPartId,
  isEnclosureTransparent,
  issueCameraCommand,
  selectPart,
  toggleEnclosure,
  bAxisStatus,
  linearAxisStatus,
  toolStatus,
  observedToolpath,
  selectedRunLabel,
  toolpathStatus,
  isToolpathVisible,
  toggleToolpath,
}: {
  model?: MachineTwinModel;
  selectedPartId?: MachineInspectionPartId;
  isEnclosureTransparent: boolean;
  issueCameraCommand: (type: CameraCommandType, partId?: MachineInspectionPartId) => void;
  selectPart: (partId?: MachineInspectionPartId) => void;
  toggleEnclosure: () => void;
  bAxisStatus: string;
  linearAxisStatus: string;
  toolStatus: string;
  observedToolpath: FactorySceneProps["observedToolpath"];
  selectedRunLabel?: string;
  toolpathStatus?: string;
  isToolpathVisible: boolean;
  toggleToolpath: () => void;
}) {
  const cameraButtons: Array<[string, string, CameraCommandType]> = [
    ["축소", "−", "ZOOM_OUT"],
    ["확대", "+", "ZOOM_IN"],
    ["전체 보기", "⌂", "RESET"],
  ];
  return (
    <aside className="machine-inspection-panel" aria-label="3D 카메라와 부품 검사">
      <div className="camera-button-grid" role="group" aria-label="3D 카메라 조작">
        {cameraButtons.map(([label, symbol, command]) => (
          <button
            type="button"
            aria-label={label}
            title={label}
            key={command}
            onClick={() => issueCameraCommand(command)}
          >
            <span aria-hidden="true">{symbol}</span>
          </button>
        ))}
      </div>
      <label className="inspection-part-select">
        <span>부품 살펴보기</span>
        <select
          aria-label="부품 살펴보기"
          value={selectedPartId ?? ""}
          disabled={!model}
          onChange={(event) => selectPart(
            event.target.value
              ? event.target.value as MachineInspectionPartId
              : undefined,
          )}
        >
          <option value="">부품을 선택하세요</option>
          {model && Object.entries(model.inspection.parts).map(([id, part]) => (
            <option value={id} key={id}>
              {id === "toolMount" ? `${part.label} · ${toolStatus}` : part.label}
            </option>
          ))}
        </select>
      </label>
      <details className="inspection-options">
        <summary>보기 옵션</summary>
        <label>
          <input
            type="checkbox"
            checked={isEnclosureTransparent}
            disabled={!model?.inspection.enclosure}
            onChange={toggleEnclosure}
          />
          외함 반투명
        </label>
        <label>
          <input
            type="checkbox"
            checked={isToolpathVisible}
            disabled={!observedToolpath}
            onChange={toggleToolpath}
          />
          관측 경로
        </label>
        {!model?.inspection.enclosure && model && (
          <span className="inspection-capability-note">이 모델은 외함 투명화를 지원하지 않습니다.</span>
        )}
      </details>
      <details className="scene-model-info">
        <summary>모델 정보</summary>
        <span>범용 수직형 CNC 표현</span>
        <span>밝은 원판: RPM에 반응하는 스핀들 표시</span>
        <span>위쪽 표시등: 현재 상태</span>
        <span>바닥의 노란 원: 선택된 설비</span>
        <span>대표 공작물: SIMULATED</span>
        <span>공장 배치: SIMULATED_LAYOUT</span>
        <span>{bAxisStatus}</span>
        <span>{linearAxisStatus}</span>
        <span>관측 변화 OBSERVED · 기준 자세·축척 SIMULATED</span>
        <span>{toolStatus}</span>
        {observedToolpath && <>
          <span>{selectedRunLabel ?? "선택한 가공"}의 관측 위치 {observedToolpath.points.length}점을 연결한 경로</span>
          <span>관측 범위 상자는 실제 절삭 흔적이나 기계 이동 한계가 아닙니다.</span>
        </>}
        {!observedToolpath && toolpathStatus && <span>{toolpathStatus}</span>}
      </details>
      <span className="visually-hidden" role="status">
        {selectedPartId && model ? `${model.inspection.parts[selectedPartId].label} 선택됨` : "선택한 부품 없음"}
      </span>
      <details className="inspection-help">
        <summary>조작법</summary>
        <span>드래그 회전 · 휠 확대 · 방향키 회전 · +/- 확대 · Home 전체 보기 · Esc 선택 해제</span>
      </details>
    </aside>
  );
}

function toolStatus(visualState: FactorySceneProps["visualState"]): string {
  return visualState?.tool === undefined
    ? "활성 공구 · 확인할 수 없음"
    : `활성 공구 ${visualState.tool} · OBSERVED · 형상 미확인`;
}

function bAxisStatus(
  binding: FactorySceneProps["machineBinding"],
  visualState: FactorySceneProps["visualState"],
): string {
  const observed = visualState?.bAxisAngleDegrees;
  const value = observed === undefined ? "관찰값 없음" : `${observed}°`;
  if (!binding.bAxisCoordinateMapping) {
    return `B축 ${value} · 좌표 매핑 검증 전 · unavailable`;
  }
  return `B축 ${value} · 검증된 좌표 매핑`;
}

function linearAxisStatus(
  binding: FactorySceneProps["machineBinding"],
  visualState: FactorySceneProps["visualState"],
  model: MachineTwinModel | undefined,
): string {
  if (model && !model.linearMotion) {
    return "XYZ 이동 · 이 모델의 이동 노드 unavailable";
  }
  if (!binding.linearAxisCoordinateMappings) {
    return "XYZ 이동 · 좌표 매핑 unavailable";
  }
  const positions = new Map(visualState?.axisPositions?.map((position) => [position.axis, position]));
  return `XYZ 이동 · ${(["X", "Y", "Z"] as const)
    .map((axis) => {
      const position = positions.get(axis);
      return position ? `${axis} ${position.millimeters.toFixed(2)} mm` : `${axis} unavailable`;
    })
    .join(" · ")}`;
}

function keyboardCameraCommand(key: string): CameraCommandType | "CLEAR_SELECTION" | undefined {
  if (key === "ArrowLeft") return "ROTATE_LEFT";
  if (key === "ArrowRight") return "ROTATE_RIGHT";
  if (key === "ArrowUp") return "ROTATE_UP";
  if (key === "ArrowDown") return "ROTATE_DOWN";
  if (key === "+" || key === "=") return "ZOOM_IN";
  if (key === "-" || key === "_") return "ZOOM_OUT";
  if (key === "Home") return "RESET";
  if (key === "Escape") return "CLEAR_SELECTION";
  return undefined;
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
