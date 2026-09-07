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
import {
  mapObservedAxisDelta,
  type LinearAxis,
  type LinearAxisTranslation,
} from "../domain/linearAxisCoordinateMapping";

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
  const linearAxisTranslations = observedAxisTranslations(binding, visualState);
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
            linearAxisTranslations={linearAxisTranslations}
            activeToolLabel={toolLabel(visualState)}
            {...inspectionProps}
          />
        }
        onError={onAssetFallback}
      >
        <FactoryAssetModel
          asset={binding.asset}
          visualPresentation={visualPresentation}
          bAxisRotation={availableBaxisRotation}
          linearAxisTranslations={linearAxisTranslations}
          activeToolLabel={toolLabel(visualState)}
          {...inspectionProps}
        />
      </AssetErrorBoundary>
      {visualState?.selected && (
        // Selection is interface state, not machine state. An amber ring read as a warning and,
        // being unlit, outshone the machine it was pointing at, so the cue is neutral and quiet.
        <mesh name="selected-machine-cue" rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.03, 0]}>
          <ringGeometry args={[2.1, 2.2, 48]} />
          <meshBasicMaterial color="#dbe8ed" transparent opacity={0.55} />
        </mesh>
      )}
    </group>
  );
}

function observedAxisTranslations(
  binding: MachineSceneBinding,
  visualState: MachineVisualState | undefined,
): Partial<Record<LinearAxis, Extract<LinearAxisTranslation, { availability: "AVAILABLE" }>>> {
  if (!binding.linearAxisCoordinateMappings || !visualState || visualState.stale) return {};
  const translations: Partial<
    Record<LinearAxis, Extract<LinearAxisTranslation, { availability: "AVAILABLE" }>>
  > = {};
  for (const position of visualState.axisPositions ?? []) {
    const mapped = mapObservedAxisDelta(
      position.millimeters,
      position.unit,
      position.sourceDataItemId,
      binding.linearAxisCoordinateMappings[position.axis],
    );
    if (mapped.availability === "AVAILABLE") translations[position.axis] = mapped;
  }
  return translations;
}

function toolLabel(visualState: MachineVisualState | undefined): string {
  return visualState?.tool === undefined
    ? "관측 공구 번호 · 확인할 수 없음"
    : `관측 공구 번호 ${visualState.tool} · 형상 미확인`;
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
