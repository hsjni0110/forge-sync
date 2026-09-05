import type { FactoryAsset, GlbFactoryAsset } from "../../domain/factoryAsset";
import { loadVerifiedGlbAsset } from "../../adapters/verifiedGlbAsset";
import { createProceduralMachineModel } from "./createProceduralMachineModel";
import {
  MACHINE_NODE_NAMES, MachineModelContractError, type MachineModelProvider,
  type MachineTwinModel, createMachineInspectionMetadata, validateMachineTwinModel,
} from "./machineTwinModel";

export class ProceduralMachineModelProvider implements MachineModelProvider {
  loadMachine(): MachineTwinModel {
    return createProceduralMachineModel();
  }
}

export class GlbMachineModelProvider implements MachineModelProvider {
  private modelPromise: Promise<MachineTwinModel> | undefined;

  constructor(private readonly asset: GlbFactoryAsset) {}

  loadMachine(): Promise<MachineTwinModel> {
    this.modelPromise ??= loadVerifiedGlbAsset(this.asset)
      .then((loaded) => this.createValidatedModel(loaded.scene));
    return this.modelPromise;
  }

  private createValidatedModel(loadedScene: import("three").Object3D): MachineTwinModel {
    const root = loadedScene.clone(true);
    root.name = MACHINE_NODE_NAMES.root;
    const nodes = {
      mainSpindle: findUnique(root, MACHINE_NODE_NAMES.mainSpindle),
      mainChuck: findUnique(root, MACHINE_NODE_NAMES.mainChuck),
      bAxisPivot: findUnique(root, MACHINE_NODE_NAMES.bAxisPivot),
      millingHead: findUnique(root, MACHINE_NODE_NAMES.millingHead),
      toolMount: findUnique(root, MACHINE_NODE_NAMES.toolMount),
      workpieceMount: findUnique(root, MACHINE_NODE_NAMES.workpieceMount),
    };
    const enclosure = findOptionalUnique(root, "cnc-enclosure");
    const model: MachineTwinModel = {
      root,
      nodes,
      statusMaterials: [],
      inspection: createMachineInspectionMetadata(
        nodes,
        enclosure ? { node: enclosure, materials: collectMaterials(enclosure) } : undefined,
      ),
      provenance: { representation: "THIRD_PARTY_GLB", workpiece: "UNSPECIFIED" },
    };
    return validateMachineTwinModel(model);
  }
}

function findOptionalUnique(
  root: import("three").Object3D,
  name: string,
): import("three").Object3D | undefined {
  const matches: import("three").Object3D[] = [];
  root.traverse((node) => { if (node.name === name) matches.push(node); });
  if (matches.length > 1) {
    throw new MachineModelContractError(`Machine model has duplicate ${name} nodes`);
  }
  return matches[0];
}

function collectMaterials(root: import("three").Object3D): import("three").Material[] {
  const materials = new Set<import("three").Material>();
  root.traverse((node) => {
    if (!("material" in node)) return;
    const mesh = node as import("three").Mesh;
    if (Array.isArray(mesh.material)) {
      mesh.material = mesh.material.map((material) => material.clone());
      for (const material of mesh.material) materials.add(material);
      return;
    }
    mesh.material = mesh.material.clone();
    materials.add(mesh.material);
  });
  return [...materials];
}

const glbProviders = new WeakMap<GlbFactoryAsset, GlbMachineModelProvider>();

export function providerFor(asset: FactoryAsset): MachineModelProvider {
  if (asset.representation === "PROCEDURAL") {
    return new ProceduralMachineModelProvider();
  }
  const cached = glbProviders.get(asset);
  if (cached) return cached;
  const provider = new GlbMachineModelProvider(asset);
  glbProviders.set(asset, provider);
  return provider;
}

function findUnique(root: import("three").Object3D, name: string): import("three").Object3D {
  const matches: import("three").Object3D[] = [];
  root.traverse((node) => { if (node.name === name) matches.push(node); });
  if (matches.length !== 1) {
    throw new MachineModelContractError(`Machine model requires exactly one ${name} node`);
  }
  return matches[0];
}
