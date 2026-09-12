import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Suspense, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { GlbFactoryAsset } from "../domain/factoryAsset";
import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import { deriveMachineVisualPresentation } from "../domain/machineVisualPresentation";
import { MAZAK01_OBSERVED_DELTA_MAPPINGS } from "../adapters/mazak01ObservedDeltaMapping";
import FactoryScene from "./FactoryScene";
import type { SceneToolpath } from "../../toolpath/domain/observedToolpath";

const loadVerifiedGlbAsset = vi.hoisted(() => vi.fn());
const canvasBehavior = vi.hoisted(() => ({ rendersScene: true }));
const cameraCommands = vi.hoisted(() => [] as unknown[]);
const reducedMotionValues = vi.hoisted(() => [] as boolean[]);
const bottomObstructions = vi.hoisted(() => [] as unknown[]);

vi.mock("../adapters/verifiedGlbAsset", () => ({ loadVerifiedGlbAsset }));
vi.mock("./MachineInspectionOverlay", () => ({ MachineInspectionOverlay: () => null }));
vi.mock("./CameraNavigationRig", () => ({
  CameraNavigationRig: ({ command, isReducedMotion, bottomObstructionFraction }: {
    command?: unknown;
    isReducedMotion: boolean;
    bottomObstructionFraction?: number;
  }) => {
    if (command) cameraCommands.push(command);
    reducedMotionValues.push(isReducedMotion);
    bottomObstructions.push(bottomObstructionFraction);
    return null;
  },
}));

vi.mock("@react-three/fiber", async () => {
  const React = await import("react");
  return {
    Canvas: ({
      children,
      fallback,
    }: {
      children: ReactNode;
      fallback: ReactNode;
    }) => {
      return React.createElement(
        "div",
        { "data-testid": "mock-canvas" },
        canvasBehavior.rendersScene ? children : null,
        React.createElement("canvas", undefined, fallback),
      );
    },
    useFrame: () => undefined,
  };
});

beforeEach(() => {
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
    {
      getExtension: () => ({ loseContext: vi.fn() }),
    } as unknown as WebGL2RenderingContext,
  );
});

afterEach(() => {
  cleanup();
  loadVerifiedGlbAsset.mockReset();
  canvasBehavior.rendersScene = true;
  cameraCommands.length = 0;
  reducedMotionValues.length = 0;
  bottomObstructions.length = 0;
  vi.restoreAllMocks();
});

describe("FactoryScene asset isolation", () => {
  it("does not report WebGL failure merely because Canvas fallback content mounts", async () => {
    canvasBehavior.rendersScene = false;
    const onUnavailable = vi.fn();

    render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={onUnavailable}
      />,
    );

    expect(await screen.findByTestId("mock-canvas")).toBeTruthy();
    expect(onUnavailable).not.toHaveBeenCalled();
  });

  it("reports WebGL failure when a WebGL2 context cannot be created", async () => {
    vi.mocked(HTMLCanvasElement.prototype.getContext).mockReturnValue(null);
    const onUnavailable = vi.fn();

    render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={onUnavailable}
      />,
    );

    await waitFor(() => expect(onUnavailable).toHaveBeenCalledWith("WEBGL"));
    expect(screen.queryByTestId("mock-canvas")).toBeNull();
  });

  it("reports a lost graphics context so the scene stops showing a blank canvas", async () => {
    const onUnavailable = vi.fn();

    render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={onUnavailable}
      />,
    );
    const canvas = (await screen.findByTestId("mock-canvas")).querySelector("canvas");

    canvas?.dispatchEvent(new Event("webglcontextlost", { bubbles: false, cancelable: true }));

    await waitFor(() => expect(onUnavailable).toHaveBeenCalledWith("WEBGL_CONTEXT_LOST"));
  });

  it.each(["GLB request returned 404", "GLB payload is invalid"])(
    "uses the generic machine primitive when %s",
    async (message) => {
      loadVerifiedGlbAsset.mockImplementation(() => {
        throw new Error(message);
      });
      const onAssetFallback = vi.fn();
      vi.spyOn(console, "error").mockImplementation(() => undefined);

      const { container } = render(
        <Suspense fallback={<div>asset loading</div>}>
          <FactoryScene
            machineBinding={binding(externalAsset)}
            visualState={visualState}
            visualPresentation={visualPresentation}
            onSelectMachine={vi.fn()}
            onAssetFallback={onAssetFallback}
            onUnavailable={vi.fn()}
          />
        </Suspense>,
      );

      await waitFor(() => expect(onAssetFallback).toHaveBeenCalledOnce());
      expect(container.querySelector('primitive[name="machine-root"]')).toBeTruthy();
    },
  );

  it("falls back atomically when GLB node contract validation fails", async () => {
    loadVerifiedGlbAsset.mockImplementation(() => {
      throw new Error("Machine model node main-spindle is missing");
    });
    const onAssetFallback = vi.fn();
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const { container } = render(
      <Suspense fallback={<div>asset loading</div>}>
        <FactoryScene
          machineBinding={binding(externalAsset)}
          visualState={visualState}
          visualPresentation={visualPresentation}
          onSelectMachine={vi.fn()}
          onAssetFallback={onAssetFallback}
          onUnavailable={vi.fn()}
        />
      </Suspense>,
    );

    await waitFor(() => expect(onAssetFallback).toHaveBeenCalledOnce());
    expect(container.querySelectorAll('primitive[name="machine-root"]')).toHaveLength(1);
  });

  it("selects the same machine from the 3D object and accessible floating label", async () => {
    const onSelectMachine = vi.fn();
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={onSelectMachine}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    await screen.findByText("Twin v4");
    expect(container.querySelector('mesh[name="selected-machine-cue"]')).toBeTruthy();
    fireEvent.click(container.querySelector('group[name="mazak01"]') as Element);
    fireEvent.click(
      screen.getByRole("button", { name: /Mazak01 Twin v4.*선택됨/ }),
    );
    expect(onSelectMachine).toHaveBeenNthCalledWith(1, "Mazak01");
    expect(onSelectMachine).toHaveBeenNthCalledWith(2, "Mazak01");
  });

  it("renders the procedural model with named operator cues", async () => {
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    await screen.findByText("Twin v4");
    expect(container.querySelector('primitive[name="machine-root"]')).toBeTruthy();
    expect(screen.getByText("범용 수직형 CNC 표현")).toBeTruthy();
    expect(screen.getByText(/밝은 원판: RPM에 반응하는 스핀들 표시/)).toBeTruthy();
  });

  it("provides the complete functional node hierarchy and simulated workpiece provenance", async () => {
    const toolpath: SceneToolpath = {
      points: [
        { replaySequence: 10, position: [0, 0, 0] },
        { replaySequence: 12, position: [0.2, 0.1, -0.1] },
      ],
      envelope: { minimum: [0, 0, -0.1], maximum: [0.2, 0.1, 0] },
    };
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
        observedToolpath={toolpath}
        selectedRunLabel="PGM 114"
        functionalPresentation={{
          cursorLabel: "Replay #12 · Twin v4",
          execution: "가동 중",
          rpm: "1,200 rpm · OBSERVED",
          axes: "X 80.08 · Y -68.79 · Z 9.64 mm · OBSERVED",
          tool: "공구 13 · OBSERVED · 형상 미확인",
          currentRun: "현재 재생 가공 · PGM 114 · DERIVED",
          selectedPath: "선택 경로 · PGM 155 · 2점 · OBSERVED_PATH",
          bAxis: "B축 · 좌표 매핑 검증 전",
          cAxis: "C축 위치 · unavailable",
          isLiveMotionAllowed: true,
        }}
      />,
    );

    await screen.findByText("Twin v4");
    expect(container.querySelector('primitive[name="machine-root"]')).toBeTruthy();
    expect(screen.getByText(/대표 공작물: SIMULATED/)).toBeTruthy();
    expect(screen.getByRole("region", { name: "현재 동작" })).toBeTruthy();
    expect(screen.getByText("현재 재생 가공 · PGM 114 · DERIVED")).toBeTruthy();
    expect(screen.getByText("선택 경로 · PGM 155 · 2점 · OBSERVED_PATH")).toBeTruthy();
    expect(screen.getByText("C축 위치 · unavailable")).toBeTruthy();
    expect(screen.getByText(/공장 배치: SIMULATED_LAYOUT/)).toBeTruthy();
    expect(screen.getByText(/B축 45° · 좌표 매핑 검증 전 · unavailable/)).toBeTruthy();
    expect(screen.getByText(/XYZ 이동 · X 80.08 mm · Y -68.79 mm · Z 9.64 mm/)).toBeTruthy();
    expect(screen.getByText(/관측 변화 OBSERVED · 기준 자세·축척 SIMULATED/)).toBeTruthy();
    expect(container.querySelector('primitive[name="observed-toolpath-trail"]')).toBeTruthy();
    expect(container.querySelector('primitive[name="observed-toolpath-envelope"]')).toBeTruthy();
    expect(screen.getByText(/PGM 114의 관측 위치 2점을 연결한 경로/)).toBeTruthy();
    expect(screen.getByText(/실제 절삭 흔적이나 기계 이동 한계가 아닙니다/)).toBeTruthy();
    fireEvent.click(screen.getByRole("checkbox", { name: "관측 경로" }));
    expect(container.querySelector('primitive[name="observed-toolpath-trail"]')).toBeNull();
  });

  it("keeps camera controls compact while preserving keyboard commands and part focus", async () => {
    render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        isReducedMotion
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    const viewport = await screen.findByLabelText("3D 조작 영역");
    fireEvent.click(screen.getByRole("button", { name: "확대" }));
    fireEvent.keyDown(viewport, { key: "ArrowLeft" });
    fireEvent.change(screen.getByRole("combobox", { name: "부품 살펴보기" }), {
      target: { value: "mainChuck" },
    });

    expect(cameraCommands).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: "ZOOM_IN" }),
      expect.objectContaining({ type: "ROTATE_LEFT" }),
      expect.objectContaining({ type: "FOCUS_PART", partId: "mainChuck" }),
    ]));
    expect(screen.getByRole<HTMLSelectElement>("combobox", { name: "부품 살펴보기" }).value)
      .toBe("mainChuck");
    expect(screen.queryByRole("button", { name: "왼쪽 회전" })).toBeNull();
    expect(screen.queryByRole("button", { name: "선택 부품 맞춤" })).toBeNull();
    expect(screen.getByText("보기 옵션")).toBeTruthy();
    expect(screen.getByText("모델 정보")).toBeTruthy();
    expect(reducedMotionValues).toContain(true);
  });

  it("opens inspection guidance in overlay panels without growing the camera control strip", async () => {
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        isReducedMotion
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    await screen.findByLabelText("3D 조작 영역");

    expect(
      container.querySelector('.inspection-options > [role="group"][aria-label="보기 옵션 내용"]'),
    ).toBeTruthy();
    expect(
      container.querySelector('.inspection-help > [role="group"][aria-label="조작법 내용"]'),
    ).toBeTruthy();
  });

  it("keeps the selection cue out of the status colour vocabulary", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );
    await screen.findByText("Twin v4");

    const cue = container.querySelector('mesh[name="selected-machine-cue"] meshBasicMaterial');
    const statusColours = ["#f7c948", "#ff5f5f", "#83e6cb", "#83b7e6", "#bda6e3"];

    expect(cue).toBeTruthy();
    expect(statusColours).not.toContain(cue?.getAttribute("color"));
  });

  it("shows only the machine, its state and speed on the label and leaves the rest to readers", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const { container } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );
    const label = await screen.findByRole("button", { name: /Mazak01 Twin v4.*선택됨/ });

    const hidden = [...label.querySelectorAll(".visually-hidden")].map((node) =>
      node.textContent?.trim(),
    );

    expect(hidden.some((text) => text?.includes("시각 회전"))).toBe(true);
    expect(hidden).toContain("선택됨");
    expect(container.querySelector(".floating-machine-label")?.textContent).toContain("Twin v4");
  });

  it("tells the camera how much of the viewport the overlays cover", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={visualState}
        visualPresentation={visualPresentation}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );
    await screen.findByText("Twin v4");

    const reported = bottomObstructions.at(-1);

    expect(typeof reported).toBe("number");
    expect(reported as number).toBeGreaterThanOrEqual(0);
    expect(reported as number).toBeLessThanOrEqual(0.8);
  });

  it("renders textual warning and stale cues without relying on color", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const warningState = { ...visualState, health: "WARNING" as const };
    const { container, rerender } = render(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={warningState}
        visualPresentation={deriveMachineVisualPresentation(warningState, false)}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    expect(
      await screen.findByRole("button", { name: /Mazak01 Twin v4 주의 49 RPM/ }),
    ).toBeTruthy();

    const staleState = {
      ...warningState,
      connectivity: "STALE" as const,
      stale: true,
    };
    rerender(
      <FactoryScene
        machineBinding={binding(proceduralAsset)}
        visualState={staleState}
        visualPresentation={deriveMachineVisualPresentation(staleState, false)}
        onSelectMachine={vi.fn()}
        onAssetFallback={vi.fn()}
        onUnavailable={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("button", {
        name: /Mazak01 Twin v4 오래된 데이터 49 RPM 시각 회전 꺼짐/,
      }),
    ).toBeTruthy();
    expect(container.querySelector('primitive[name="machine-root"]')).toBeTruthy();
  });
});

function binding(asset: typeof proceduralAsset | GlbFactoryAsset): MachineSceneBinding {
  return {
    machineId: "Mazak01",
    sceneNodeId: "mazak01",
    asset: asset.representation === "GLB" ? { ...asset } : asset,
    position: [0, 0, 0],
    rotation: [0, 0, 0],
    scale: [1, 1, 1],
    spatialProvenance: "SIMULATED_LAYOUT",
    spatialAvailability: "TWIN",
    visualSpindleSourceDataItemId: "Mazak01-C_5",
    linearAxisCoordinateMappings: MAZAK01_OBSERVED_DELTA_MAPPINGS,
  };
}

const visualState: MachineVisualState = {
  machineId: "Mazak01",
  twinVersion: 4,
  connectivity: "ONLINE",
  execution: "ACTIVE",
  health: "NORMAL",
  rpm: 49,
  rpmSourceDataItemId: "Mazak01-C_5",
  bAxisAngleDegrees: 45,
  bAxisAngleUnit: "DEGREE",
  bAxisAngleSourceDataItemId: "Mazak01-B_4",
  bAxisAngleSourceObservedAt: "2016-10-05T09:16:39.557Z",
  axisPositions: [
    {
      axis: "X",
      millimeters: 80.078834,
      unit: "MILLIMETER",
      sourceDataItemId: "Mazak01-X_1",
      sourceObservedAt: "2016-10-05T09:01:41.165Z",
    },
    {
      axis: "Y",
      millimeters: -68.786629,
      unit: "MILLIMETER",
      sourceDataItemId: "Mazak01-Y_1",
      sourceObservedAt: "2016-10-05T09:01:41.165Z",
    },
    {
      axis: "Z",
      millimeters: 9.635998,
      unit: "MILLIMETER",
      sourceDataItemId: "Mazak01-Z_1",
      sourceObservedAt: "2016-10-05T08:49:23.254Z",
    },
  ],
  tool: "13",
  stale: false,
  selected: true,
};

const visualPresentation = deriveMachineVisualPresentation(visualState, false);

const proceduralAsset = {
  assetId: "procedural-cnc",
  displayName: "Procedural CNC test asset",
  representation: "PROCEDURAL" as const,
  origin: "PROJECT_PROCEDURAL" as const,
  sourceLocator: "apps/factory-web/src/features/factory3d/ui/model/createProceduralMachineModel.ts",
  license: {
    expression: "NOASSERTION",
    evidenceState: "TO_VERIFY" as const,
    attribution: "Test-only descriptor",
  },
};

const externalAsset: GlbFactoryAsset = {
  assetId: "external-cnc",
  displayName: "External CNC test asset",
  representation: "GLB",
  origin: "THIRD_PARTY",
  uri: "/assets/external-cnc.glb",
  sha256: "0".repeat(64),
  byteLength: 1,
  license: {
    expression: "NOASSERTION",
    evidenceState: "TO_VERIFY",
    attribution: "Test-only descriptor; no asset bytes are distributed",
  },
};
