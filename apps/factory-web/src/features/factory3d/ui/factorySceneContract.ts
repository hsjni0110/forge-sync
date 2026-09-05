import type { ComponentType } from "react";

import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";

export type SceneUnavailableReason = "BUNDLE_OR_SCENE" | "WEBGL";

export interface FactorySceneProps {
  machineBinding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  visualPresentation: MachineVisualPresentation;
  isReducedMotion?: boolean;
  onSelectMachine: (machineId: string) => void;
  onAssetFallback: () => void;
  onUnavailable: (reason: SceneUnavailableReason) => void;
}

export type FactorySceneLoader = () => Promise<{
  default: ComponentType<FactorySceneProps>;
}>;
