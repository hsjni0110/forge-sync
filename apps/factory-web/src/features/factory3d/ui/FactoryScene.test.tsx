import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Suspense, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { GlbFactoryAsset } from "../domain/factoryAsset";
import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import { deriveMachineVisualPresentation } from "../domain/machineVisualPresentation";
import FactoryScene from "./FactoryScene";

const loadVerifiedGlbAsset = vi.hoisted(() => vi.fn());
const canvasBehavior = vi.hoisted(() => ({ rendersScene: true }));

vi.mock("../adapters/verifiedGlbAsset", () => ({ loadVerifiedGlbAsset }));

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
      expect(container.querySelector('group[name="generic-cnc-primitive"]')).toBeTruthy();
    },
  );

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

  it("renders a recognizable generic vertical CNC with named operator cues", async () => {
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
    for (const partName of [
      "cnc-enclosure",
      "cnc-work-envelope",
      "cnc-work-table",
      "cnc-spindle-head",
      "cnc-control-panel",
      "cnc-status-tower",
    ]) {
      expect(container.querySelector(`[name="${partName}"]`)).toBeTruthy();
    }
    expect(screen.getByText("범용 수직형 CNC 표현")).toBeTruthy();
    expect(screen.getByText(/밝은 원판: RPM에 반응하는 스핀들 표시/)).toBeTruthy();
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
    expect(
      container.querySelector(
        'group[name="generic-cnc-primitive"] meshstandardmaterial[color="#48545a"][opacity="0.62"]',
      ),
    ).toBeTruthy();
  });
});

function binding(asset: typeof proceduralAsset | GlbFactoryAsset): MachineSceneBinding {
  return {
    machineId: "Mazak01",
    sceneNodeId: "mazak01",
    asset,
    position: [0, 0, 0],
    rotation: [0, 0, 0],
    scale: [1, 1, 1],
    spatialProvenance: "SIMULATED_LAYOUT",
    visualSpindleSourceDataItemId: "Mazak01-C_5",
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
  sourceLocator: "apps/factory-web/src/features/factory3d/ui/GenericMachinePrimitive.tsx",
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
