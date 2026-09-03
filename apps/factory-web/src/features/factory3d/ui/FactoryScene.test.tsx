import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { Suspense, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { GlbFactoryAsset } from "../domain/factoryAsset";
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
    useFrame: (callback: () => void) => React.useEffect(callback, [callback]),
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
        asset={proceduralAsset}
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
        asset={proceduralAsset}
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
            asset={externalAsset}
            onAssetFallback={onAssetFallback}
            onUnavailable={vi.fn()}
          />
        </Suspense>,
      );

      await waitFor(() => expect(onAssetFallback).toHaveBeenCalledOnce());
      expect(container.querySelector('group[name="generic-cnc-primitive"]')).toBeTruthy();
    },
  );
});

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
