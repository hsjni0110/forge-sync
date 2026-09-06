import { use, useMemo, type ReactNode } from "react";

import type { FactoryAsset } from "../domain/factoryAsset";
import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";
import { AssetErrorBoundary } from "./AssetErrorBoundary";
import { GenericMachinePrimitive } from "./GenericMachinePrimitive";
import { MachineModelView, type MachineInspectionViewProps } from "./MachineModelView";
import { providerFor } from "./model/machineModelProviders";
import type { MachineTwinModel } from "./model/machineTwinModel";
import { mapBaxisAngleToRotation } from "../domain/bAxisCoordinateMapping";

export function MachineTwin({
  binding,
  visualState,
  visualPresentation,
  onSelectMachine,
  onAssetFallback,
  ...inspectionProps
}: MachineInspectionViewProps & {
  binding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  visualPresentation: MachineVisualPresentation;
  onSelectMachine: (machineId: string) => void;
  onAssetFallback: () => void;
}) {
  const bAxisRotation =
    binding.bAxisCoordinateMapping &&
    visualState?.bAxisAngleDegrees !== undefined &&
    !visualState.stale &&
    visualState.bAxisAngleSourceDataItemId === binding.bAxisCoordinateMapping.sourceDataItemId
      ? mapBaxisAngleToRotation(
          visualState.bAxisAngleDegrees,
          visualState.bAxisAngleUnit,
          binding.bAxisCoordinateMapping,
        )
      : undefined;
  const availableBaxisRotation =
    bAxisRotation?.availability === "AVAILABLE" ? bAxisRotation : undefined;
  return (
    <group
      name={binding.sceneNodeId}
      position={binding.position}
      rotation={binding.rotation}
      scale={binding.scale}
      onClick={(event) => {
        event.stopPropagation();
        onSelectMachine(binding.machineId);
      }}
    >
      <AssetErrorBoundary
        fallback={
          <GenericMachinePrimitive
            visualPresentation={visualPresentation}
            bAxisRotation={availableBaxisRotation}
            {...inspectionProps}
          />
        }
        onError={onAssetFallback}
      >
        <FactoryAssetModel
          asset={binding.asset}
          visualPresentation={visualPresentation}
          bAxisRotation={availableBaxisRotation}
          {...inspectionProps}
        />
      </AssetErrorBoundary>
      {visualState?.selected && (
        <mesh name="selected-machine-cue" rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.03, 0]}>
          <ringGeometry args={[2.05, 2.25, 48]} />
          <meshBasicMaterial color="#f7c948" transparent opacity={0.9} />
        </mesh>
      )}
    </group>
  );
}

function FactoryAssetModel({
  asset,
  visualPresentation,
  ...inspectionProps
}: MachineInspectionViewProps & {
  asset: FactoryAsset;
  visualPresentation: MachineVisualPresentation;
}): ReactNode {
  if (asset.representation === "PROCEDURAL") {
    return (
      <GenericMachinePrimitive
        visualPresentation={visualPresentation}
        {...inspectionProps}
      />
    );
  }
  return (
    <GlbMachine
      asset={asset}
      visualPresentation={visualPresentation}
      {...inspectionProps}
    />
  );
}

function GlbMachine({
  asset,
  visualPresentation,
  ...inspectionProps
}: MachineInspectionViewProps & {
  asset: Extract<FactoryAsset, { representation: "GLB" }>;
  visualPresentation: MachineVisualPresentation;
}) {
  const modelPromise = useMemo(
    () => providerFor(asset).loadMachine(asset.assetId) as Promise<MachineTwinModel>,
    [asset],
  );
  const model = use(modelPromise);
  return (
    <MachineModelView
      model={model}
      visualPresentation={visualPresentation}
      {...inspectionProps}
    />
  );
}
