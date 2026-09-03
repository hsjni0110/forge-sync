import { useFrame } from "@react-three/fiber";
import { useRef } from "react";
import type { Mesh } from "three";

import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";

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

export function MachineStatusBeacon({
  visualPresentation,
}: {
  visualPresentation: MachineVisualPresentation;
}) {
  const beacon = useRef<Mesh>(null);
  const elapsedSeconds = useRef(0);

  useFrame((_state, deltaSeconds) => {
    if (!beacon.current) {
      return;
    }
    if (!visualPresentation.isBeaconPulsing) {
      beacon.current.scale.setScalar(1);
      return;
    }
    elapsedSeconds.current += deltaSeconds;
    beacon.current.scale.setScalar(1 + Math.sin(elapsedSeconds.current * 5) * 0.12);
  });

  return (
    <mesh ref={beacon} name="machine-status-beacon" position={[1.05, 2.95, 0.8]}>
      <sphereGeometry args={[0.16, 20, 20]} />
      <meshStandardMaterial
        color={BEACON_COLORS[visualPresentation.status]}
        emissive={BEACON_COLORS[visualPresentation.status]}
        emissiveIntensity={0.65}
      />
    </mesh>
  );
}
