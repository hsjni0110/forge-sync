import { describe, expect, it, vi } from "vitest";
import { Group } from "three";

import type { GlbFactoryAsset } from "../../domain/factoryAsset";
import { loadVerifiedGlbAsset } from "../../adapters/verifiedGlbAsset";
import { GlbMachineModelProvider } from "./machineModelProviders";

vi.mock("../../adapters/verifiedGlbAsset", () => ({
  loadVerifiedGlbAsset: vi.fn(),
}));

describe("GLB machine model provider", () => {
  it("rejects the entire model when a required functional node is missing", async () => {
    vi.mocked(loadVerifiedGlbAsset).mockResolvedValue({
      scene: new Group(),
    } as Awaited<ReturnType<typeof loadVerifiedGlbAsset>>);

    await expect(
      new GlbMachineModelProvider(externalAsset).loadMachine(),
    ).rejects.toThrow("requires exactly one main-spindle node");
  });

  it("keeps a valid GLB usable while reporting enclosure inspection as unsupported", async () => {
    vi.mocked(loadVerifiedGlbAsset).mockResolvedValue({
      scene: validSceneWithoutInspectableEnclosure(),
    } as Awaited<ReturnType<typeof loadVerifiedGlbAsset>>);

    const model = await new GlbMachineModelProvider(externalAsset).loadMachine();

    expect(model.inspection.enclosure).toBeUndefined();
    expect(model.inspection.parts.toolMount.node).toBe(model.nodes.toolMount);
  });
});

function validSceneWithoutInspectableEnclosure(): Group {
  const root = namedGroup("loaded-root");
  const staticBody = namedGroup("static-body");
  const spindleGroup = namedGroup("main-spindle-group");
  spindleGroup.add(
    namedGroup("main-spindle"),
    namedGroup("main-chuck"),
    namedGroup("workpiece-mount"),
  );
  const bAxis = namedGroup("b-axis-pivot");
  const head = namedGroup("milling-head");
  const toolSpindle = namedGroup("tool-spindle");
  toolSpindle.add(namedGroup("tool-mount"));
  head.add(toolSpindle);
  bAxis.add(head);
  root.add(staticBody, spindleGroup, bAxis);
  return root;
}

function namedGroup(name: string): Group {
  const group = new Group();
  group.name = name;
  return group;
}

const externalAsset: GlbFactoryAsset = {
  assetId: "incomplete-glb",
  displayName: "Incomplete GLB",
  representation: "GLB",
  origin: "THIRD_PARTY",
  uri: "/assets/incomplete.glb",
  sha256: "0".repeat(64),
  byteLength: 1,
  license: {
    expression: "NOASSERTION",
    evidenceState: "TO_VERIFY",
    attribution: "Test-only descriptor",
  },
};
