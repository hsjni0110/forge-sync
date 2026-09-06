import {
  BoxGeometry,
  CylinderGeometry,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  PlaneGeometry,
  SphereGeometry,
} from "three";
import {
  MACHINE_NODE_NAMES,
  createMachineInspectionMetadata,
  type MachineTwinModel,
  validateMachineTwinModel,
} from "./machineTwinModel";

export function createProceduralMachineModel(): MachineTwinModel {
  const root = group(MACHINE_NODE_NAMES.root);
  const staticBody = group(MACHINE_NODE_NAMES.staticBody);
  const statusMaterials: MeshStandardMaterial[] = [];
  const enclosure = createEnclosure(staticBody, statusMaterials);
  const mainSpindleGroup = group(MACHINE_NODE_NAMES.mainSpindleGroup);
  const mainSpindle = createMainSpindle();
  const mainChuck = createChuck();
  const workpieceMount = createWorkpieceMount();
  mainSpindleGroup.add(mainSpindle, mainChuck, workpieceMount);
  const bAxisPivot = group(MACHINE_NODE_NAMES.bAxisPivot);
  bAxisPivot.position.set(0.55, 2.05, 0.55);
  const zAxisCarriage = group("z-axis-carriage");
  const xAxisCarriage = group("x-axis-carriage");
  const yAxisCarriage = group("y-axis-carriage");
  zAxisCarriage.add(xAxisCarriage);
  xAxisCarriage.add(yAxisCarriage);
  yAxisCarriage.add(bAxisPivot);
  const millingHead = createMillingHead();
  const toolSpindle = group(MACHINE_NODE_NAMES.toolSpindle);
  toolSpindle.position.set(0, -0.48, 0);
  toolSpindle.add(
    mesh(
      "tool-spindle-body",
      new CylinderGeometry(0.18, 0.12, 0.45, 24),
      material("#748990"),
    ),
  );
  const toolMount = group(MACHINE_NODE_NAMES.toolMount);
  toolMount.position.set(0, -0.28, 0);
  toolMount.add(
    mesh(
      "tool-identity-placeholder",
      new CylinderGeometry(0.09, 0.06, 0.22, 20),
      material("#9aa9ae"),
    ),
  );
  toolMount.userData.representation = "SHAPE_UNVERIFIED";
  toolSpindle.add(toolMount);
  millingHead.add(toolSpindle);
  bAxisPivot.add(millingHead);
  const statusBeacon = createStatusBeacon();
  root.add(staticBody, mainSpindleGroup, zAxisCarriage, statusBeacon);
  const nodes = {
    mainSpindle,
    mainChuck,
    bAxisPivot,
    millingHead,
    toolMount,
    workpieceMount,
  };
  return validateMachineTwinModel({
    root,
    nodes,
    linearMotion: { xAxisCarriage, yAxisCarriage, zAxisCarriage },
    statusMaterials,
    statusBeacon,
    inspection: createMachineInspectionMetadata(nodes, {
      node: enclosure,
      materials: statusMaterials,
    }),
    provenance: { representation: "PROJECT_PROCEDURAL", workpiece: "SIMULATED" },
  });
}

function createEnclosure(parent: Group, materials: MeshStandardMaterial[]): Group {
  parent.add(mesh("cnc-base", new BoxGeometry(3.5, 0.36, 2.7), material("#172b33"), [0, 0.18, 0]));
  const enclosure = group("cnc-enclosure");
  for (const [size, position] of [
    [[3.3, 0.42, 2.45], [0, 2.75, -0.15]], [[0.36, 2.45, 2.45], [-1.47, 1.55, -0.15]],
    [[0.36, 2.45, 2.45], [1.47, 1.55, -0.15]], [[2.65, 2.45, 0.18], [0, 1.55, -1.25]],
  ] as const) {
    const surface = material("#46545b");
    materials.push(surface);
    enclosure.add(mesh("", new BoxGeometry(...size), surface, position));
  }
  const workArea = group("cnc-work-area");
  workArea.add(mesh("cnc-work-window", new PlaneGeometry(1.9, 1.3),
    new MeshStandardMaterial({ color: "#254651", transparent: true, opacity: 0.42 }), [0, 1.62, 1.19]));
  const bed = mesh("cnc-machine-bed", new BoxGeometry(1.55, 0.18, 0.75), material("#8da0a8"), [0, 0.83, 0.62]);
  const panel = mesh("cnc-control-panel", new BoxGeometry(0.62, 1.28, 0.25), material("#263c45"), [1.82, 1.65, 0.72]);
  parent.add(enclosure, workArea, bed, panel);
  return enclosure;
}

function createMainSpindle(): Group {
  const spindle = group(MACHINE_NODE_NAMES.mainSpindle);
  spindle.position.set(-0.72, 1.45, 0.63);
  const disk = mesh("spindle-visual-cue", new CylinderGeometry(0.32, 0.32, 0.12, 32), material("#9ccac0"));
  disk.rotation.x = Math.PI / 2;
  spindle.add(disk, mesh("spindle-index-mark", new BoxGeometry(0.25, 0.055, 0.04),
    new MeshBasicMaterial({ color: "#08151b" }), [0.18, 0, 0.08]));
  return spindle;
}

function createChuck(): Group {
  const chuck = group(MACHINE_NODE_NAMES.mainChuck);
  chuck.position.set(-0.72, 1.45, 0.75);
  const body = mesh("chuck-body", new CylinderGeometry(0.38, 0.38, 0.18, 24), material("#748990"));
  body.rotation.x = Math.PI / 2;
  chuck.add(body);
  return chuck;
}

function createWorkpieceMount(): Group {
  const mount = group(MACHINE_NODE_NAMES.workpieceMount);
  mount.position.set(-0.25, 1.45, 0.75);
  const stock = mesh("representative-workpiece", new CylinderGeometry(0.18, 0.18, 0.75, 24), material("#b88a55"));
  stock.rotation.z = Math.PI / 2;
  stock.userData.provenance = "SIMULATED";
  mount.add(stock);
  return mount;
}

function createMillingHead(): Group {
  const head = group(MACHINE_NODE_NAMES.millingHead);
  head.add(mesh("milling-head-body", new BoxGeometry(0.72, 0.72, 0.62), material("#c4ced1")));
  return head;
}

function createStatusBeacon(): Mesh {
  return mesh("machine-status-beacon", new SphereGeometry(0.16, 20, 20),
    new MeshStandardMaterial({ color: "#91a9b4", emissive: "#91a9b4", emissiveIntensity: 0.65 }), [1.05, 2.95, 0.8]);
}

function group(name: string): Group {
  const value = new Group();
  value.name = name;
  return value;
}
function material(color: string): MeshStandardMaterial {
  return new MeshStandardMaterial({ color, metalness: 0.55, roughness: 0.48 });
}
function mesh(
  name: string,
  geometry: Mesh["geometry"],
  meshMaterial: Mesh["material"],
  position: readonly [number, number, number] = [0, 0, 0],
): Mesh {
  const value = new Mesh(geometry, meshMaterial);
  value.name = name;
  value.position.set(...position);
  value.castShadow = true;
  value.receiveShadow = true;
  return value;
}
