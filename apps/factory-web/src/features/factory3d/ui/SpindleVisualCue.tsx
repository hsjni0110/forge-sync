import { useFrame } from "@react-three/fiber";
import { useRef } from "react";
import type { Group } from "three";

import {
  nextVisualSpindleRotation,
  type MachineVisualPresentation,
} from "../domain/machineVisualPresentation";

export function SpindleVisualCue({
  visualPresentation,
}: {
  visualPresentation: MachineVisualPresentation;
}) {
  const spindleGroup = useRef<Group>(null);

  useFrame((_state, deltaSeconds) => {
    if (!spindleGroup.current || !visualPresentation.isSpindleAnimating) {
      return;
    }
    spindleGroup.current.rotation.z = nextVisualSpindleRotation(
      spindleGroup.current.rotation.z,
      visualPresentation,
      deltaSeconds,
    );
  });

  return (
    <group
      ref={spindleGroup}
      name="visual-spindle-cue"
      position={[0, 1.45, 1.25]}
    >
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[0.32, 0.32, 0.12, 32]} />
        <meshStandardMaterial color="#9ccac0" metalness={0.7} roughness={0.3} />
      </mesh>
      <mesh position={[0.18, 0, 0.08]}>
        <boxGeometry args={[0.25, 0.055, 0.04]} />
        <meshBasicMaterial color="#08151b" />
      </mesh>
    </group>
  );
}
