import defaultManifestDocument from "./defaultFactoryAssetManifest.json";
import { decodeFactoryAssetManifest } from "./factoryAssetManifest";
import type { MachineSceneBinding } from "../domain/machineVisualState";
import { MAZAK01_OBSERVED_DELTA_MAPPINGS } from "./mazak01ObservedDeltaMapping";

const defaultAsset = decodeFactoryAssetManifest(defaultManifestDocument).assets[0];

export const MAZAK01_SCENE_BINDING: MachineSceneBinding = {
  machineId: "Mazak01",
  sceneNodeId: "mazak01",
  asset: defaultAsset,
  position: [0, 0.2, 0],
  rotation: [0, -0.35, 0],
  scale: [1, 1, 1],
  spatialProvenance: "SIMULATED_LAYOUT",
  spatialAvailability: "FALLBACK",
  // This is an explicit visual-cue input, not a claim that C_5 is the physical primary spindle.
  visualSpindleSourceDataItemId: "Mazak01-C_5",
  linearAxisCoordinateMappings: MAZAK01_OBSERVED_DELTA_MAPPINGS,
};

export function sceneBindingFromTwin(
  spatial: import("../domain/machineVisualState").MachineSpatialLayout | undefined,
): MachineSceneBinding {
  if (!spatial || spatial.assetId !== defaultAsset.assetId) return MAZAK01_SCENE_BINDING;
  return {
    ...MAZAK01_SCENE_BINDING,
    sceneNodeId: spatial.sceneNodeId,
    position: spatial.position,
    rotation: spatial.rotation,
    scale: spatial.scale,
    spatialProvenance: spatial.provenance,
    spatialAvailability: "TWIN",
  };
}
