import { useThree } from "@react-three/fiber";
import { useEffect } from "react";
import {
  Box3,
  BoxHelper,
  CanvasTexture,
  Sprite,
  SpriteMaterial,
  Vector3,
} from "three";

import type {
  MachineInspectionPartId,
  MachineTwinModel,
} from "./model/machineTwinModel";

export function MachineInspectionOverlay({
  model,
  hoveredPartId,
  selectedPartId,
}: {
  model: MachineTwinModel;
  hoveredPartId?: MachineInspectionPartId;
  selectedPartId?: MachineInspectionPartId;
}) {
  const scene = useThree((state) => state.scene);

  useEffect(() => {
    const partId = selectedPartId ?? hoveredPartId;
    if (!partId) return;
    const part = model.inspection.parts[partId];
    const helper = new BoxHelper(part.node, selectedPartId ? 0xf7c948 : 0x67cdb3);
    helper.name = selectedPartId ? "selected-part-helper" : "hovered-part-helper";
    scene.add(helper);
    let label: Sprite | undefined;
    if (selectedPartId) {
      label = createLabel(part.label, part.node);
      scene.add(label);
    }
    return () => {
      scene.remove(helper);
      helper.geometry.dispose();
      helper.material.dispose();
      if (label) {
        scene.remove(label);
        label.material.map?.dispose();
        label.material.dispose();
      }
    };
  }, [hoveredPartId, model, scene, selectedPartId]);
  return null;
}

function createLabel(text: string, node: import("three").Object3D): Sprite {
  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 96;
  const context = canvas.getContext("2d");
  if (context) {
    context.fillStyle = "rgba(8, 21, 27, 0.94)";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.strokeStyle = "#f7c948";
    context.lineWidth = 4;
    context.strokeRect(2, 2, canvas.width - 4, canvas.height - 4);
    context.fillStyle = "#ffffff";
    context.font = "600 30px sans-serif";
    context.textAlign = "center";
    context.textBaseline = "middle";
    context.fillText(text, canvas.width / 2, canvas.height / 2);
  }
  const sprite = new Sprite(
    new SpriteMaterial({ map: new CanvasTexture(canvas), depthTest: false }),
  );
  const bounds = new Box3().setFromObject(node);
  const position = bounds.getCenter(new Vector3());
  position.y = bounds.max.y + Math.max(bounds.getSize(new Vector3()).y * 0.35, 0.25);
  sprite.position.copy(position);
  sprite.scale.set(2.4, 0.45, 1);
  sprite.renderOrder = 100;
  sprite.name = "selected-part-label";
  return sprite;
}
