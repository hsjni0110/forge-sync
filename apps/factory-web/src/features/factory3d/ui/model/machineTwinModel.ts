import type { Material, Mesh, Object3D } from "three";

export const MACHINE_NODE_NAMES = {
  root: "machine-root",
  staticBody: "static-body",
  mainSpindleGroup: "main-spindle-group",
  mainSpindle: "main-spindle",
  mainChuck: "main-chuck",
  workpieceMount: "workpiece-mount",
  bAxisPivot: "b-axis-pivot",
  millingHead: "milling-head",
  toolSpindle: "tool-spindle",
  toolMount: "tool-mount",
} as const;

export interface MachineTwinNodes {
  mainSpindle: Object3D;
  mainChuck: Object3D;
  bAxisPivot: Object3D;
  millingHead: Object3D;
  toolMount: Object3D;
  workpieceMount: Object3D;
}

export interface MachineTwinModel {
  root: Object3D;
  nodes: MachineTwinNodes;
  statusMaterials: Material[];
  statusBeacon?: Mesh;
  inspection: MachineInspectionMetadata;
  provenance: {
    representation: "PROJECT_PROCEDURAL" | "THIRD_PARTY_GLB";
    workpiece: "SIMULATED" | "UNSPECIFIED";
  };
}

export type MachineInspectionPartId = keyof MachineTwinNodes;

export interface MachineInspectionPart {
  label: string;
  node: Object3D;
}

export interface MachineInspectionMetadata {
  parts: Record<MachineInspectionPartId, MachineInspectionPart>;
  enclosure?: {
    node: Object3D;
    materials: Material[];
  };
}

export function createMachineInspectionMetadata(
  nodes: MachineTwinNodes,
  enclosure?: MachineInspectionMetadata["enclosure"],
): MachineInspectionMetadata {
  return {
    parts: {
      mainSpindle: { label: "Main Spindle", node: nodes.mainSpindle },
      mainChuck: { label: "Main Chuck", node: nodes.mainChuck },
      bAxisPivot: { label: "B-axis Pivot", node: nodes.bAxisPivot },
      millingHead: { label: "Milling Head", node: nodes.millingHead },
      toolMount: { label: "Tool Mount", node: nodes.toolMount },
      workpieceMount: {
        label: "대표 공작물 · SIMULATED",
        node: nodes.workpieceMount,
      },
    },
    enclosure,
  };
}

export interface MachineModelProvider {
  loadMachine(machineId: string): MachineTwinModel | Promise<MachineTwinModel>;
}

export class MachineModelContractError extends Error {}

export function validateMachineTwinModel(model: MachineTwinModel): MachineTwinModel {
  const { root, nodes } = model;
  requireNode(root, MACHINE_NODE_NAMES.root);
  for (const name of Object.values(MACHINE_NODE_NAMES)) {
    requireExactlyOneNamedNode(root, name);
  }
  requireExactlyOneDirectChild(root, MACHINE_NODE_NAMES.staticBody);
  requireExactlyOneDirectChild(root, MACHINE_NODE_NAMES.mainSpindleGroup);
  requireNode(nodes.mainSpindle, MACHINE_NODE_NAMES.mainSpindle);
  requireNode(nodes.mainChuck, MACHINE_NODE_NAMES.mainChuck);
  requireNode(nodes.workpieceMount, MACHINE_NODE_NAMES.workpieceMount);
  requireNode(nodes.bAxisPivot, MACHINE_NODE_NAMES.bAxisPivot);
  requireNode(nodes.millingHead, MACHINE_NODE_NAMES.millingHead);
  requireNode(nodes.toolMount, MACHINE_NODE_NAMES.toolMount);
  requireDescendant(root, nodes.mainSpindle);
  requireDescendant(root, nodes.mainChuck);
  requireDescendant(root, nodes.workpieceMount);
  requireDirectParent(nodes.mainSpindle, MACHINE_NODE_NAMES.mainSpindleGroup);
  requireDirectParent(nodes.mainChuck, MACHINE_NODE_NAMES.mainSpindleGroup);
  requireDirectParent(nodes.workpieceMount, MACHINE_NODE_NAMES.mainSpindleGroup);
  requireDirectParent(nodes.bAxisPivot, MACHINE_NODE_NAMES.root);
  requireDirectObjectParent(nodes.millingHead, nodes.bAxisPivot);
  requireDirectParent(nodes.toolMount, MACHINE_NODE_NAMES.toolSpindle);
  requireDirectObjectParent(nodes.toolMount.parent, nodes.millingHead);
  if (new Set(Object.values(nodes)).size !== Object.values(nodes).length) {
    throw new MachineModelContractError("Machine model functional nodes must be unique");
  }
  for (const [partId, part] of Object.entries(model.inspection.parts)) {
    if (part.node !== nodes[partId as MachineInspectionPartId]) {
      throw new MachineModelContractError(
        `Inspection part ${partId} must reference its functional node`,
      );
    }
  }
  return model;
}

function requireExactlyOneNamedNode(root: Object3D, nodeName: string): void {
  let count = 0;
  root.traverse((node) => {
    if (node.name === nodeName) count += 1;
  });
  if (count !== 1) {
    throw new MachineModelContractError(
      `Machine model requires exactly one ${nodeName} node`,
    );
  }
}

function requireExactlyOneDirectChild(parent: Object3D, childName: string): void {
  if (parent.children.filter((child) => child.name === childName).length !== 1) {
    throw new MachineModelContractError(
      `${parent.name} requires exactly one direct ${childName} child`,
    );
  }
}

function requireDirectParent(node: Object3D, expectedParentName: string): void {
  if (node.parent?.name !== expectedParentName) {
    throw new MachineModelContractError(`${node.name} must be inside ${expectedParentName}`);
  }
}

function requireNode(node: Object3D, expectedName: string): void {
  if (node.name !== expectedName) {
    throw new MachineModelContractError(`Machine model node ${expectedName} is missing`);
  }
}

function requireDescendant(root: Object3D, node: Object3D): void {
  if (!isDescendant(node, root)) {
    throw new MachineModelContractError(`${node.name} is outside machine-root`);
  }
}

function requireDirectObjectParent(
  node: Object3D | null,
  expectedParent: Object3D,
): void {
  if (node?.parent !== expectedParent) {
    throw new MachineModelContractError(
      `${node?.name ?? "missing node"} has an invalid parent hierarchy`,
    );
  }
}

function isDescendant(node: Object3D, ancestor: Object3D): boolean {
  for (let current: Object3D | null = node; current; current = current.parent) {
    if (current === ancestor) return true;
  }
  return false;
}
