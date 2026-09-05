import { useMemo } from "react";

import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";
import { MachineModelView, type MachineInspectionViewProps } from "./MachineModelView";
import { createProceduralMachineModel } from "./model/createProceduralMachineModel";

export function GenericMachinePrimitive({
  visualPresentation,
  ...inspectionProps
}: Partial<MachineInspectionViewProps> & {
  visualPresentation?: MachineVisualPresentation;
}) {
  const model = useMemo(createProceduralMachineModel, []);
  return (
    <MachineModelView
      model={model}
      visualPresentation={visualPresentation}
      selectedPartId={inspectionProps.selectedPartId}
      hoveredPartId={inspectionProps.hoveredPartId}
      isEnclosureTransparent={inspectionProps.isEnclosureTransparent ?? false}
      onSelectPart={inspectionProps.onSelectPart ?? (() => undefined)}
      onHoverPart={inspectionProps.onHoverPart ?? (() => undefined)}
      onModelReady={inspectionProps.onModelReady ?? (() => undefined)}
    />
  );
}
