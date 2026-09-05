import { useFrame } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import { Color, MeshStandardMaterial } from "three";
import {
  nextVisualSpindleRotation,
  type MachineVisualPresentation,
} from "../../domain/machineVisualPresentation";
import type { MachineTwinModel } from "./machineTwinModel";

const SURFACE_COLORS: Record<MachineVisualPresentation["materialTone"], string> = {
  STALE: "#48545a",
  OFFLINE: "#303a3f",
  FAULT: "#713b3b",
  WARNING: "#756329",
  HOLD: "#655776",
  ACTIVE: "#315f58",
  STOPPED: "#315563",
  IDLE: "#315563",
  READY: "#315563",
  UNKNOWN: "#46545b",
};

const BEACON_COLORS: Record<MachineVisualPresentation["status"], string> = {
  STALE: "#82939a",
  OFFLINE: "#69767c",
  FAULT: "#ff5f5f",
  WARNING: "#f7c948",
  HOLD: "#bda6e3",
  ACTIVE: "#83e6cb",
  STOPPED: "#91a9b4",
  IDLE: "#91a9b4",
  READY: "#83b7e6",
  UNKNOWN: "#91a9b4",
};

export function MachineModelBinding({
  model,
  visualPresentation,
  isEnclosureTransparent = false,
}: {
  model: MachineTwinModel;
  visualPresentation: MachineVisualPresentation;
  isEnclosureTransparent?: boolean;
}) {
  const beaconElapsedSeconds = useRef(0);

  useEffect(() => {
    const isMuted = ["STALE", "OFFLINE"].includes(visualPresentation.materialTone);
    for (const material of model.statusMaterials) {
      if (!(material instanceof MeshStandardMaterial)) continue;
      material.color = new Color(SURFACE_COLORS[visualPresentation.materialTone]);
      material.transparent = isMuted || isEnclosureTransparent;
      material.opacity = isEnclosureTransparent ? 0.2 : isMuted ? 0.62 : 1;
      material.depthWrite = !isEnclosureTransparent;
      material.needsUpdate = true;
    }
    const beaconMaterial = model.statusBeacon?.material;
    if (beaconMaterial instanceof MeshStandardMaterial) {
      const beaconColor = new Color(BEACON_COLORS[visualPresentation.status]);
      beaconMaterial.color = beaconColor;
      beaconMaterial.emissive = beaconColor;
      beaconMaterial.needsUpdate = true;
    }
  }, [isEnclosureTransparent, model, visualPresentation.materialTone, visualPresentation.status]);

  useFrame((_state, deltaSeconds) => {
    if (visualPresentation.isSpindleAnimating) {
      model.nodes.mainSpindle.rotation.z = nextVisualSpindleRotation(
        model.nodes.mainSpindle.rotation.z,
        visualPresentation,
        deltaSeconds,
      );
    }
    if (!model.statusBeacon) return;
    if (!visualPresentation.isBeaconPulsing) {
      model.statusBeacon.scale.setScalar(1);
      return;
    }
    beaconElapsedSeconds.current += deltaSeconds;
    model.statusBeacon.scale.setScalar(
      1 + Math.sin(beaconElapsedSeconds.current * 5) * 0.12,
    );
  });
  return null;
}
