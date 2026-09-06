import { describe, expect, it } from "vitest";
import { MAZAK01_SCENE_BINDING, sceneBindingFromTwin } from "./defaultMachineSceneBinding";

describe("Twin spatial scene binding", () => {
  it("moves the renderer using validated Twin spatial values", () => {
    const binding = sceneBindingFromTwin({
      assetId: "cnc-generic-primitive-v1", sceneNodeId: "configured-node",
      position: [4, 5, 6],
      rotation: [0, 1, 0],
      scale: [2, 2, 2], provenance: "SIMULATED_LAYOUT",
    });
    expect(binding).toMatchObject({
      sceneNodeId: "configured-node", position: [4, 5, 6], rotation: [0, 1, 0],
      scale: [2, 2, 2], spatialAvailability: "TWIN",
    });
  });

  it("uses the complete safe fallback when spatial is absent or references an unknown asset", () => {
    expect(sceneBindingFromTwin(undefined)).toBe(MAZAK01_SCENE_BINDING);
    expect(sceneBindingFromTwin({
      assetId: "unknown", sceneNodeId: "node", position: [9, 9, 9],
      rotation: [0, 0, 0],
      scale: [1, 1, 1], provenance: "SIMULATED_LAYOUT",
    })).toBe(MAZAK01_SCENE_BINDING);
  });
});
