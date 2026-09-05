import { Canvas, useFrame } from "@react-three/fiber";
import { useCallback, useEffect, useRef, useState } from "react";

import { FloatingMachineLabel } from "./FloatingMachineLabel";
import type { FactorySceneProps } from "./factorySceneContract";
import { MachineTwin } from "./MachineTwin";
import { CameraNavigationRig, type CameraCommand, type CameraCommandType } from "./CameraNavigationRig";
import type { MachineInspectionPartId, MachineTwinModel } from "./model/machineTwinModel";

export default function FactoryScene({
  machineBinding,
  visualState,
  visualPresentation,
  isReducedMotion = false,
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
        <color attach="background" args={["#08151b"]} />
        <ambientLight intensity={1.5} />
        <directionalLight position={[4, 8, 5]} intensity={2.5} castShadow />
        <pointLight position={[0, 2.4, 1.8]} intensity={7} distance={5} color="#d8fff4" />
        <gridHelper args={[18, 18, "#315963", "#18323a"]} />
        <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
          <planeGeometry args={[18, 18]} />
          <meshStandardMaterial color="#0b2028" roughness={0.9} />
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
        <aside className="scene-visual-legend" aria-label="3D 설비 표현 설명">
          <strong>범용 수직형 CNC 표현</strong>
          <span>밝은 원판: RPM에 반응하는 스핀들 표시</span>
          <span>위쪽 표시등: 현재 상태</span>
          <span>바닥의 노란 원: 선택된 설비</span>
          <span>대표 공작물: SIMULATED</span>
          <span>공장 배치: SIMULATED_LAYOUT</span>
        </aside>
        {isSceneReady && <span className="visually-hidden">3D 장면 준비됨</span>}
      </div>
      <MachineInspectionControls
        model={model}
        selectedPartId={selectedPartId}
        isEnclosureTransparent={isEnclosureTransparent}
        issueCameraCommand={issueCameraCommand}
        selectPart={selectPart}
        toggleEnclosure={() => setIsEnclosureTransparent((current) => !current)}
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
}: {
  model?: MachineTwinModel;
  selectedPartId?: MachineInspectionPartId;
  isEnclosureTransparent: boolean;
  issueCameraCommand: (type: CameraCommandType, partId?: MachineInspectionPartId) => void;
  selectPart: (partId?: MachineInspectionPartId) => void;
  toggleEnclosure: () => void;
}) {
  const cameraButtons: Array<[string, CameraCommandType]> = [
    ["확대", "ZOOM_IN"], ["축소", "ZOOM_OUT"], ["왼쪽 회전", "ROTATE_LEFT"],
    ["오른쪽 회전", "ROTATE_RIGHT"], ["위로 회전", "ROTATE_UP"],
    ["아래로 회전", "ROTATE_DOWN"], ["전체 보기", "RESET"],
  ];
  return (
    <aside className="machine-inspection-panel" aria-label="3D 카메라와 부품 검사">
      <div className="camera-button-grid" role="group" aria-label="3D 카메라 조작">
        {cameraButtons.map(([label, command]) => (
          <button type="button" key={command} onClick={() => issueCameraCommand(command)}>
            {label}
          </button>
        ))}
        <button
          type="button"
          disabled={!selectedPartId}
          onClick={() => issueCameraCommand("FOCUS_PART", selectedPartId)}
        >
          선택 부품 맞춤
        </button>
      </div>
      <div className="inspection-part-list" role="group" aria-label="기능 부품 선택">
        {model && Object.entries(model.inspection.parts).map(([id, part]) => (
          <button
            type="button"
            key={id}
            aria-pressed={selectedPartId === id}
            onClick={() => selectPart(id as MachineInspectionPartId)}
          >
            {part.label}
          </button>
        ))}
      </div>
      <button
        type="button"
        aria-pressed={isEnclosureTransparent}
        disabled={!model?.inspection.enclosure}
        onClick={toggleEnclosure}
      >
        외함 반투명
      </button>
      {!model?.inspection.enclosure && model && (
        <span className="inspection-capability-note">이 모델은 외함 투명화를 지원하지 않습니다.</span>
      )}
      <span className="visually-hidden" role="status">
        {selectedPartId && model ? `${model.inspection.parts[selectedPartId].label} 선택됨` : "선택한 부품 없음"}
      </span>
      <span className="inspection-keyboard-help">방향키 회전 · +/- 확대 · Home 전체 보기 · Esc 선택 해제</span>
    </aside>
  );
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
