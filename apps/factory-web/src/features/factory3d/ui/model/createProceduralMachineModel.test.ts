import { describe, expect, it } from "vitest";
import { createProceduralMachineModel } from "./createProceduralMachineModel";

describe("procedural machine model contract", () => {
  it("returns stable functional references in the required hierarchy", () => {
    const model = createProceduralMachineModel();
    expect(model.root.name).toBe("machine-root");
    expect(model.root.getObjectByName("static-body")?.parent).toBe(model.root);
    expect(model.nodes.mainSpindle.parent?.name).toBe("main-spindle-group");
    expect(model.nodes.mainChuck.parent?.name).toBe("main-spindle-group");
    expect(model.nodes.workpieceMount.parent?.name).toBe("main-spindle-group");
    expect(model.nodes.bAxisPivot.parent).toBe(model.root);
    expect(model.nodes.millingHead.parent).toBe(model.nodes.bAxisPivot);
    expect(model.nodes.toolMount.parent?.name).toBe("tool-spindle");
    expect(model.nodes.toolMount.parent?.parent).toBe(model.nodes.millingHead);
  });

  it("builds recognizable static geometry with simulated workpiece provenance", () => {
    const model = createProceduralMachineModel();
    for (const name of ["cnc-enclosure", "cnc-work-area", "cnc-machine-bed",
      "main-chuck", "milling-head", "representative-workpiece"]) {
      expect(model.root.getObjectByName(name), name).toBeTruthy();
    }
    expect(model.root.getObjectByName("representative-workpiece")?.userData.provenance).toBe("SIMULATED");
    expect(model.provenance).toEqual({ representation: "PROJECT_PROCEDURAL", workpiece: "SIMULATED" });
    expect(model.nodes.toolMount.userData.representation).toBe("SHAPE_UNVERIFIED");
    expect(model.nodes.toolMount.getObjectByName("tool-identity-placeholder")).toBeTruthy();
  });

  it("provides explicit inspection references and enclosure materials", () => {
    const model = createProceduralMachineModel();

    expect(Object.keys(model.inspection.parts)).toEqual([
      "mainSpindle",
      "mainChuck",
      "bAxisPivot",
      "millingHead",
      "toolMount",
      "workpieceMount",
    ]);
    expect(model.inspection.parts.workpieceMount).toMatchObject({
      label: "대표 공작물 · SIMULATED",
      node: model.nodes.workpieceMount,
    });
    expect(model.inspection.enclosure?.materials.length).toBeGreaterThan(0);
  });
});
