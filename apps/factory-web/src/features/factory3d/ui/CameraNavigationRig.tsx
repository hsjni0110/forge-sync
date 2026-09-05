import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import { Box3, PerspectiveCamera, Vector3 } from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

import { calculateCameraFrame } from "./cameraFraming";
import type { MachineTwinModel, MachineInspectionPartId } from "./model/machineTwinModel";

export type CameraCommandType =
  | "RESET" | "FOCUS_PART" | "ZOOM_IN" | "ZOOM_OUT"
  | "ROTATE_LEFT" | "ROTATE_RIGHT" | "ROTATE_UP" | "ROTATE_DOWN";

export interface CameraCommand {
  sequence: number;
  type: CameraCommandType;
  partId?: MachineInspectionPartId;
}

export function CameraNavigationRig({ model, command, isReducedMotion }: {
  model?: MachineTwinModel;
  command?: CameraCommand;
  isReducedMotion: boolean;
}) {
  const { camera, gl, size } = useThree();
  const controlsRef = useRef<OrbitControls | undefined>(undefined);
  const transitionRef = useRef<{
    elapsed: number;
    fromPosition: Vector3;
    toPosition: Vector3;
    fromTarget: Vector3;
    toTarget: Vector3;
  } | undefined>(undefined);

  useEffect(() => {
    const controls = new OrbitControls(camera, gl.domElement);
    controls.enableDamping = !isReducedMotion;
    controls.dampingFactor = 0.08;
    controls.minPolarAngle = (20 * Math.PI) / 180;
    controls.maxPolarAngle = (85 * Math.PI) / 180;
    controls.addEventListener("start", () => {
      transitionRef.current = undefined;
    });
    controlsRef.current = controls;
    return () => controls.dispose();
  }, [camera, gl.domElement, isReducedMotion]);

  useEffect(() => {
    if (!model || !(camera instanceof PerspectiveCamera)) return;
    const controls = controlsRef.current;
    if (!controls) return;
    const target = command?.type === "FOCUS_PART" && command.partId
      ? model.inspection.parts[command.partId].node
      : model.root;
    const frame = calculateCameraFrame(
      new Box3().setFromObject(target), camera.fov, size.width / size.height,
    );
    controls.minDistance = frame.minDistance;
    controls.maxDistance = frame.maxDistance;
    if (!command || command.type === "RESET" || command.type === "FOCUS_PART") {
      const direction = new Vector3(1, 0.65, 1).normalize();
      const toPosition = frame.target.clone().addScaledVector(direction, frame.distance);
      if (isReducedMotion) {
        camera.position.copy(toPosition);
        controls.target.copy(frame.target);
        controls.update(0);
      } else {
        transitionRef.current = {
          elapsed: 0,
          fromPosition: camera.position.clone(),
          toPosition,
          fromTarget: controls.target.clone(),
          toTarget: frame.target.clone(),
        };
      }
      return;
    }
    const angle = (10 * Math.PI) / 180;
    if (command.type === "ZOOM_IN") controls.dollyIn(1.15);
    if (command.type === "ZOOM_OUT") controls.dollyOut(1.15);
    if (command.type === "ROTATE_LEFT") controls.rotateLeft(angle);
    if (command.type === "ROTATE_RIGHT") controls.rotateLeft(-angle);
    if (command.type === "ROTATE_UP") controls.rotateUp(angle);
    if (command.type === "ROTATE_DOWN") controls.rotateUp(-angle);
    controls.update(0);
  }, [camera, command, isReducedMotion, model, size.height, size.width]);

  useFrame((_state, delta) => {
    const controls = controlsRef.current;
    const transition = transitionRef.current;
    if (!controls) return;
    if (transition) {
      transition.elapsed += delta;
      const linearProgress = Math.min(transition.elapsed / 0.25, 1);
      const progress = 1 - (1 - linearProgress) ** 3;
      camera.position.lerpVectors(
        transition.fromPosition,
        transition.toPosition,
        progress,
      );
      controls.target.lerpVectors(
        transition.fromTarget,
        transition.toTarget,
        progress,
      );
      if (linearProgress === 1) transitionRef.current = undefined;
    }
    controls.update(delta);
  });
  return null;
}
