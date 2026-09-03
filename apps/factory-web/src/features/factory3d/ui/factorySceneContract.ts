import type { ComponentType } from "react";

import type { FactoryAsset } from "../domain/factoryAsset";

export type SceneUnavailableReason = "BUNDLE_OR_SCENE" | "WEBGL";

export interface FactorySceneProps {
  asset: FactoryAsset;
  onAssetFallback: () => void;
  onUnavailable: (reason: SceneUnavailableReason) => void;
}

export type FactorySceneLoader = () => Promise<{
  default: ComponentType<FactorySceneProps>;
}>;
