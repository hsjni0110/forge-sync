import { use, type ReactNode } from "react";

import { loadVerifiedGlbAsset } from "../adapters/verifiedGlbAsset";
import type { FactoryAsset } from "../domain/factoryAsset";
import type {
  MachineSceneBinding,
  MachineVisualState,
} from "../domain/machineVisualState";
import { AssetErrorBoundary } from "./AssetErrorBoundary";
import { GenericMachinePrimitive } from "./GenericMachinePrimitive";

export function MachineTwin({
  binding,
  visualState,
  onSelectMachine,
  onAssetFallback,
}: {
  binding: MachineSceneBinding;
  visualState: MachineVisualState | undefined;
  onSelectMachine: (machineId: string) => void;
  onAssetFallback: () => void;
}) {
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
        fallback={<GenericMachinePrimitive />}
        onError={onAssetFallback}
      >
        <FactoryAssetModel asset={binding.asset} />
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

function FactoryAssetModel({ asset }: { asset: FactoryAsset }): ReactNode {
  if (asset.representation === "PROCEDURAL") {
    return <GenericMachinePrimitive />;
  }
  return <GlbMachine asset={asset} />;
}

function GlbMachine({
  asset,
}: {
  asset: Extract<FactoryAsset, { representation: "GLB" }>;
}) {
  const model = use(loadVerifiedGlbAsset(asset));
  return <primitive object={model.scene} />;
}
