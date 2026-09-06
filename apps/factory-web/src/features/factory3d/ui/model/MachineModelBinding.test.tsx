import { act, cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { deriveMachineVisualPresentation } from "../../domain/machineVisualPresentation";
import { createProceduralMachineModel } from "./createProceduralMachineModel";
import { MachineModelBinding } from "./MachineModelBinding";

const frameCallbacks = vi.hoisted(() => [] as Array<(_state: unknown, delta: number) => void>);

vi.mock("@react-three/fiber", () => ({
  useFrame: (callback: (_state: unknown, delta: number) => void) => {
    frameCallbacks.push(callback);
  },
}));

afterEach(() => {
  cleanup();
  frameCallbacks.length = 0;
});

describe("machine model runtime binding", () => {
  it("updates explicit references without recreating the model root", () => {
    const model = createProceduralMachineModel();
    const originalRoot = model.root;
    const presentation = deriveMachineVisualPresentation(
      {
        machineId: "Mazak01",
        twinVersion: 4,
        connectivity: "ONLINE",
        execution: "ACTIVE",
        health: "NORMAL",
        rpm: 500,
        rpmSourceDataItemId: "Mazak01-C_5",
        tool: "13",
        stale: false,
        selected: false,
      },
      false,
    );

    render(<MachineModelBinding model={model} visualPresentation={presentation} />);
    act(() => frameCallbacks[0]({}, 0.25));

    expect(model.root).toBe(originalRoot);
    expect(model.nodes.mainSpindle.rotation.z).toBeGreaterThan(0);
    expect(model.statusMaterials[0].opacity).toBe(1);
  });

  it("mutes materials and freezes spindle motion for stale data", () => {
    const model = createProceduralMachineModel();
    const presentation = deriveMachineVisualPresentation(
      {
        machineId: "Mazak01",
        twinVersion: 5,
        connectivity: "STALE",
        execution: "ACTIVE",
        health: "NORMAL",
        rpm: 500,
        rpmSourceDataItemId: "Mazak01-C_5",
        tool: "13",
        stale: true,
        selected: false,
      },
      false,
    );

    render(<MachineModelBinding model={model} visualPresentation={presentation} />);
    act(() => frameCallbacks[0]({}, 0.25));

    expect(model.nodes.mainSpindle.rotation.z).toBe(0);
    expect(model.statusMaterials[0].transparent).toBe(true);
    expect(model.statusMaterials[0].opacity).toBe(0.62);
  });

  it("keeps the enclosure translucent across status binding updates", () => {
    const model = createProceduralMachineModel();
    const presentation = deriveMachineVisualPresentation(undefined, false);
    const { rerender } = render(
      <MachineModelBinding
        model={model}
        visualPresentation={presentation}
        isEnclosureTransparent
      />,
    );

    rerender(
      <MachineModelBinding
        model={model}
        visualPresentation={{ ...presentation, materialTone: "WARNING" }}
        isEnclosureTransparent
      />,
    );

    expect(model.statusMaterials[0].opacity).toBe(0.2);
    expect(model.statusMaterials[0].depthWrite).toBe(false);
  });

  it("applies a validated angle only to the explicit B-axis pivot reference", () => {
    const model = createProceduralMachineModel();
    const presentation = deriveMachineVisualPresentation(undefined, false);

    render(
      <MachineModelBinding
        model={model}
        visualPresentation={presentation}
        bAxisRotation={{ availability: "AVAILABLE", radians: Math.PI / 4, rotationAxis: "z" }}
      />,
    );
    act(() => frameCallbacks[0]({}, 0.25));

    expect(model.nodes.bAxisPivot.rotation.z).toBeCloseTo(Math.PI / 4);
    expect(model.nodes.millingHead.rotation.z).toBe(0);
  });

  it("applies a validated B-axis angle immediately when motion is reduced", () => {
    const model = createProceduralMachineModel();
    render(
      <MachineModelBinding
        model={model}
        visualPresentation={deriveMachineVisualPresentation(undefined, true)}
        bAxisRotation={{ availability: "AVAILABLE", radians: -Math.PI / 6, rotationAxis: "x" }}
      />,
    );

    expect(model.nodes.bAxisPivot.rotation.x).toBeCloseTo(-Math.PI / 6);
  });
});
