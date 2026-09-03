import defaultManifestDocument from "./defaultFactoryAssetManifest.json";
import { decodeFactoryAssetManifest } from "./factoryAssetManifest";
import type { MachineSceneBinding } from "../domain/machineVisualState";

const defaultAsset = decodeFactoryAssetManifest(defaultManifestDocument).assets[0];

export const MAZAK01_SCENE_BINDING: MachineSceneBinding = {
  machineId: "Mazak01",
  sceneNodeId: "mazak01",
  asset: defaultAsset,
  position: [0, 0.2, 0],
  rotation: [0, -0.35, 0],
  scale: [1, 1, 1],
  spatialProvenance: "SIMULATED_LAYOUT",
  // This is an explicit visual-cue input, not a claim that C_5 is the physical primary spindle.
  visualSpindleSourceDataItemId: "Mazak01-C_5",
};
