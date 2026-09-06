import { useEffect } from "react";

import { MachineInspectionAppearance } from "./MachineInspectionAppearance";
import { MachineInspectionOverlay } from "./MachineInspectionOverlay";
import { MachineModelBinding } from "./model/MachineModelBinding";
import type {
  MachineInspectionPartId,
  MachineTwinModel,
} from "./model/machineTwinModel";
import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";
import type { BAxisRotation } from "../domain/bAxisCoordinateMapping";

export interface MachineInspectionViewProps {
  selectedPartId?: MachineInspectionPartId;
  hoveredPartId?: MachineInspectionPartId;
  isEnclosureTransparent: boolean;
  onSelectPart: (partId?: MachineInspectionPartId) => void;
  onHoverPart: (partId?: MachineInspectionPartId) => void;
  onModelReady: (model?: MachineTwinModel) => void;
  bAxisRotation?: Extract<BAxisRotation, { availability: "AVAILABLE" }>;
  activeToolLabel?: string;
}

export function MachineModelView({
  model,
  visualPresentation,
  selectedPartId,
  hoveredPartId,
  isEnclosureTransparent,
  onSelectPart,
  onHoverPart,
  onModelReady,
  bAxisRotation,
  activeToolLabel,
}: MachineInspectionViewProps & {
  model: MachineTwinModel;
  visualPresentation?: MachineVisualPresentation;
}) {
  useEffect(() => {
    onModelReady(model);
    return () => onModelReady(undefined);
  }, [model, onModelReady]);

  const partFromEvent = (event: { object: import("three").Object3D }) => {
    for (let node: import("three").Object3D | null = event.object; node; node = node.parent) {
      const match = Object.entries(model.inspection.parts).find(
        ([, part]) => part.node === node,
      );
      if (match) return match[0] as MachineInspectionPartId;
      if (node === model.root) break;
    }
    return undefined;
  };

  return (
    <group
      onPointerMove={(event) => onHoverPart(partFromEvent(event))}
      onPointerOut={() => onHoverPart(undefined)}
      onClick={(event) => {
        const partId = partFromEvent(event);
        if (!partId) return;
        event.stopPropagation();
        onSelectPart(partId);
      }}
    >
      <primitive object={model.root} name={model.root.name} />
      {visualPresentation && (
        <MachineModelBinding
          model={model}
          visualPresentation={visualPresentation}
          isEnclosureTransparent={isEnclosureTransparent}
          bAxisRotation={bAxisRotation}
          activeToolLabel={activeToolLabel}
        />
      )}
      <MachineInspectionAppearance
        model={model}
        isEnclosureTransparent={isEnclosureTransparent}
      />
      <MachineInspectionOverlay
        model={model}
        hoveredPartId={hoveredPartId}
        selectedPartId={selectedPartId}
      />
    </group>
  );
}
