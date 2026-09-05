import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { MachineInspectionAppearance } from "./MachineInspectionAppearance";
import { createProceduralMachineModel } from "./model/createProceduralMachineModel";

afterEach(cleanup);

describe("machine enclosure inspection appearance", () => {
  it("makes enclosure materials translucent and restores their exact state", () => {
    const model = createProceduralMachineModel();
    const materials = model.inspection.enclosure?.materials ?? [];
    model.statusMaterials = [];
    const original = materials.map((material) => ({
      transparent: material.transparent,
      opacity: material.opacity,
      depthWrite: material.depthWrite,
    }));
    const { rerender } = render(
      <MachineInspectionAppearance model={model} isEnclosureTransparent={false} />,
    );

    rerender(<MachineInspectionAppearance model={model} isEnclosureTransparent />);
    for (const material of materials) {
      expect(material.transparent).toBe(true);
      expect(material.opacity).toBe(0.2);
      expect(material.depthWrite).toBe(false);
    }

    rerender(
      <MachineInspectionAppearance model={model} isEnclosureTransparent={false} />,
    );
    materials.forEach((material, index) => {
      expect(material.transparent).toBe(original[index].transparent);
      expect(material.opacity).toBe(original[index].opacity);
      expect(material.depthWrite).toBe(original[index].depthWrite);
    });
  });
});
