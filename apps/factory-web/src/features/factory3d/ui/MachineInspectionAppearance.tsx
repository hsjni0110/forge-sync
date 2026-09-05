import { useEffect } from "react";

import type { MachineTwinModel } from "./model/machineTwinModel";

export function MachineInspectionAppearance({
  model,
  isEnclosureTransparent,
}: {
  model: MachineTwinModel;
  isEnclosureTransparent: boolean;
}) {
  useEffect(() => {
    const materials = (model.inspection.enclosure?.materials ?? []).filter(
      (material) => !model.statusMaterials.includes(material),
    );
    if (!isEnclosureTransparent) return;
    const original = materials.map((material) => ({
      material,
      transparent: material.transparent,
      opacity: material.opacity,
      depthWrite: material.depthWrite,
    }));
    for (const material of materials) {
      material.transparent = true;
      material.opacity = 0.2;
      material.depthWrite = false;
      material.needsUpdate = true;
    }
    return () => {
      for (const state of original) {
        state.material.transparent = state.transparent;
        state.material.opacity = state.opacity;
        state.material.depthWrite = state.depthWrite;
        state.material.needsUpdate = true;
      }
    };
  }, [isEnclosureTransparent, model]);
  return null;
}
