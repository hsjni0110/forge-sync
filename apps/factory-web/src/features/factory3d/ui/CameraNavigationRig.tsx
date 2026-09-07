import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import { Box3, OrthographicCamera, Vector3 } from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

import { calculateIsometricFrame } from "./cameraFraming";
import type { MachineTwinModel, MachineInspectionPartId } from "./model/machineTwinModel";

export type CameraCommandType =
  | "RESET" | "FOCUS_PART" | "ZOOM_IN" | "ZOOM_OUT"
  | "ROTATE_LEFT" | "ROTATE_RIGHT" | "ROTATE_UP" | "ROTATE_DOWN";

export interface CameraCommand {
  sequence: number;
  type: CameraCommandType;
  partId?: MachineInspectionPartId;
}

/**
 * A true isometric view: equal angles to all three axes, so no axis is foreshortened more than
 * another and the floor grid reads as the machine's own axes.
 */
const ISOMETRIC_DIRECTION = new Vector3(1, 1, 1).normalize();

/** The world direction that reads as "down the screen" for a camera placed on `direction`. */
function screenDown(direction: Vector3): Vector3 {
  const up = new Vector3(0, 1, 0);
  return up.clone().addScaledVector(direction, -up.dot(direction)).normalize().negate();
}

export function CameraNavigationRig({
  model,
  command,
  isReducedMotion,
  bottomObstructionFraction = 0,
}: {
  model?: MachineTwinModel;
  command?: CameraCommand;
  isReducedMotion: boolean;
  bottomObstructionFraction?: number;
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
    if (!model || !(camera instanceof OrthographicCamera)) return;
    const controls = controlsRef.current;
    if (!controls) return;
    const target = command?.type === "FOCUS_PART" && command.partId
      ? model.inspection.parts[command.partId].node
      : model.root;
    const frame = calculateIsometricFrame(
      new Box3().setFromObject(target),
      size.width,
      size.height,
      bottomObstructionFraction,
    );
    controls.minZoom = frame.minZoom;
    controls.maxZoom = frame.maxZoom;
    if (!command || command.type === "RESET" || command.type === "FOCUS_PART") {
      const toTarget = frame.target
        .clone()
        .addScaledVector(screenDown(ISOMETRIC_DIRECTION), frame.screenLift);
      const toPosition = toTarget.clone().addScaledVector(ISOMETRIC_DIRECTION, frame.distance);
      camera.zoom = frame.zoom;
      camera.updateProjectionMatrix();
      if (isReducedMotion) {
        camera.position.copy(toPosition);
        controls.target.copy(toTarget);
        controls.update(0);
      } else {
        transitionRef.current = {
          elapsed: 0,
          fromPosition: camera.position.clone(),
          toPosition,
          fromTarget: controls.target.clone(),
          toTarget,
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
  }, [
    bottomObstructionFraction,
    camera,
    command,
    isReducedMotion,
    model,
    size.height,
    size.width,
  ]);

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
