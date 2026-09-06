import type { ComponentType } from "react";

import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";
import type { SceneToolpath } from "../../toolpath/domain/observedToolpath";
import type { FunctionalTwinPresentation } from "../domain/functionalTwinPresentation";

export type SceneUnavailableReason = "BUNDLE_OR_SCENE" | "WEBGL";

export interface FactorySceneProps {
  machineBinding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  visualPresentation: MachineVisualPresentation;
  functionalPresentation?: FunctionalTwinPresentation;
  isReducedMotion?: boolean;
  observedToolpath?: SceneToolpath;
  selectedRunLabel?: string;
  toolpathStatus?: string;
  onSelectMachine: (machineId: string) => void;
  onAssetFallback: () => void;
  onUnavailable: (reason: SceneUnavailableReason) => void;
}

export type FactorySceneLoader = () => Promise<{
  default: ComponentType<FactorySceneProps>;
}>;
