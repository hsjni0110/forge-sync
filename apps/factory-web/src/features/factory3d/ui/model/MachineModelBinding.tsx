import { useFrame } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import { Color, MeshStandardMaterial } from "three";
import {
  nextVisualSpindleRotation,
  type MachineVisualPresentation,
} from "../../domain/machineVisualPresentation";
import type { MachineTwinModel } from "./machineTwinModel";
import type { BAxisRotation } from "../../domain/bAxisCoordinateMapping";
import type {
  LinearAxis,
  LinearAxisTranslation,
} from "../../domain/linearAxisCoordinateMapping";

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
  bAxisRotation,
  linearAxisTranslations,
  activeToolLabel,
}: {
  model: MachineTwinModel;
  visualPresentation: MachineVisualPresentation;
  isEnclosureTransparent?: boolean;
  bAxisRotation?: Extract<BAxisRotation, { availability: "AVAILABLE" }>;
  linearAxisTranslations?: Partial<
    Record<LinearAxis, Extract<LinearAxisTranslation, { availability: "AVAILABLE" }>>
  >;
  activeToolLabel?: string;
}) {
  const beaconElapsedSeconds = useRef(0);
  const bAxisTransition = useRef<{
    axis: "x" | "y" | "z";
    from: number;
    to: number;
    elapsedSeconds: number;
  } | undefined>(undefined);
  const linearTransitions = useRef<Array<{
    axis: LinearAxis;
    sceneAxis: "x" | "y" | "z";
    from: number;
    to: number;
    elapsedSeconds: number;
  }>>([]);

  useEffect(() => {
    model.nodes.toolMount.userData.activeToolLabel = activeToolLabel ?? "확인할 수 없음";
  }, [activeToolLabel, model.nodes.toolMount]);

  useEffect(() => {
    if (!bAxisRotation) return;
    if (visualPresentation.isReducedMotion || !visualPresentation.isReplayAdvancing) {
      model.nodes.bAxisPivot.rotation[bAxisRotation.rotationAxis] = bAxisRotation.radians;
      bAxisTransition.current = undefined;
      return;
    }
    bAxisTransition.current = {
      axis: bAxisRotation.rotationAxis,
      from: model.nodes.bAxisPivot.rotation[bAxisRotation.rotationAxis],
      to: bAxisRotation.radians,
      elapsedSeconds: 0,
    };
  }, [
    bAxisRotation,
    model.nodes.bAxisPivot,
    visualPresentation.isReducedMotion,
    visualPresentation.isReplayAdvancing,
  ]);

  useEffect(() => {
    if (!model.linearMotion || !linearAxisTranslations) return;
    const available = Object.values(linearAxisTranslations);
    if (visualPresentation.isReducedMotion || !visualPresentation.isReplayAdvancing) {
      for (const translation of available) {
        carriageFor(model, translation.axis).position[translation.sceneAxis] =
          translation.offsetSceneUnits;
      }
      linearTransitions.current = [];
      return;
    }
    linearTransitions.current = available.map((translation) => ({
      axis: translation.axis,
      sceneAxis: translation.sceneAxis,
      from: carriageFor(model, translation.axis).position[translation.sceneAxis],
      to: translation.offsetSceneUnits,
      elapsedSeconds: 0,
    }));
  }, [
    linearAxisTranslations,
    model,
    visualPresentation.isReducedMotion,
    visualPresentation.isReplayAdvancing,
  ]);

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
    linearTransitions.current = linearTransitions.current.filter((transition) => {
      transition.elapsedSeconds = Math.min(0.25, transition.elapsedSeconds + deltaSeconds);
      const progress = transition.elapsedSeconds / 0.25;
      carriageFor(model, transition.axis).position[transition.sceneAxis] =
        transition.from + (transition.to - transition.from) * progress;
      return progress < 1;
    });
    const transition = bAxisTransition.current;
    if (transition) {
      transition.elapsedSeconds = Math.min(0.25, transition.elapsedSeconds + deltaSeconds);
      const progress = transition.elapsedSeconds / 0.25;
      model.nodes.bAxisPivot.rotation[transition.axis] =
        transition.from + (transition.to - transition.from) * progress;
      if (progress === 1) bAxisTransition.current = undefined;
    }
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

function carriageFor(model: MachineTwinModel, axis: LinearAxis) {
  if (!model.linearMotion) throw new Error("Linear motion nodes are unavailable");
  return {
    X: model.linearMotion.xAxisCarriage,
    Y: model.linearMotion.yAxisCarriage,
    Z: model.linearMotion.zAxisCarriage,
  }[axis];
}
