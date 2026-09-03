import type { MachineVisualPresentation } from "../domain/machineVisualPresentation";

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

export function GenericMachinePrimitive({
  visualPresentation,
}: {
  visualPresentation?: MachineVisualPresentation;
}) {
  const materialTone = visualPresentation?.materialTone ?? "UNKNOWN";
  const isMuted = materialTone === "STALE" || materialTone === "OFFLINE";
  const surfaceColor = SURFACE_COLORS[materialTone];
  const surfaceMaterial = (
    <meshStandardMaterial
      color={surfaceColor}
      metalness={0.55}
      roughness={0.48}
      transparent={isMuted}
      opacity={isMuted ? 0.62 : 1}
    />
  );

  return (
    <group name="generic-cnc-primitive">
      <mesh name="cnc-base" position={[0, 0.18, 0]} castShadow receiveShadow>
        <boxGeometry args={[3.5, 0.36, 2.7]} />
        <meshStandardMaterial color="#172b33" metalness={0.65} roughness={0.58} />
      </mesh>

      <group name="cnc-enclosure">
        <mesh position={[0, 2.75, -0.15]} castShadow>
          <boxGeometry args={[3.3, 0.42, 2.45]} />
          {surfaceMaterial}
        </mesh>
        <mesh position={[-1.47, 1.55, -0.15]} castShadow>
          <boxGeometry args={[0.36, 2.45, 2.45]} />
          {surfaceMaterial}
        </mesh>
        <mesh position={[1.47, 1.55, -0.15]} castShadow>
          <boxGeometry args={[0.36, 2.45, 2.45]} />
          {surfaceMaterial}
        </mesh>
        <mesh position={[0, 1.55, -1.25]} castShadow>
          <boxGeometry args={[2.65, 2.45, 0.18]} />
          {surfaceMaterial}
        </mesh>
        <mesh position={[0, 0.48, -0.15]} castShadow>
          <boxGeometry args={[2.65, 0.34, 2.2]} />
          {surfaceMaterial}
        </mesh>
      </group>

      <group name="cnc-work-envelope">
        <mesh position={[0, 1.62, 1.05]} castShadow>
          <boxGeometry args={[2.35, 1.65, 0.12]} />
          <meshStandardMaterial color="#10232c" metalness={0.25} roughness={0.3} />
        </mesh>
        <mesh position={[0, 1.62, 1.125]}>
          <planeGeometry args={[1.9, 1.3]} />
          <meshStandardMaterial color="#254651" transparent opacity={0.42} roughness={0.18} />
        </mesh>
        <mesh position={[0, 1.62, 1.19]}>
          <boxGeometry args={[0.055, 1.3, 0.04]} />
          <meshStandardMaterial color="#8da0a8" metalness={0.75} roughness={0.3} />
        </mesh>
      </group>

      <group name="cnc-work-table" position={[0, 0.83, 0.62]}>
        <mesh castShadow>
          <boxGeometry args={[1.55, 0.18, 0.75]} />
          <meshStandardMaterial color="#8da0a8" metalness={0.82} roughness={0.28} />
        </mesh>
        {[-0.5, -0.25, 0, 0.25, 0.5].map((x) => (
          <mesh key={x} position={[x, 0.1, 0]}>
            <boxGeometry args={[0.035, 0.025, 0.7]} />
            <meshBasicMaterial color="#23343b" />
          </mesh>
        ))}
      </group>

      <group name="cnc-spindle-head" position={[0, 2.05, 0.64]}>
        <mesh castShadow>
          <boxGeometry args={[0.72, 0.72, 0.62]} />
          <meshStandardMaterial color="#c4ced1" metalness={0.68} roughness={0.35} />
        </mesh>
        <mesh position={[0, -0.48, 0]} castShadow>
          <cylinderGeometry args={[0.18, 0.12, 0.45, 24]} />
          <meshStandardMaterial color="#748990" metalness={0.85} roughness={0.25} />
        </mesh>
      </group>

      <group name="cnc-control-panel" position={[1.82, 1.65, 0.72]} rotation={[0, -0.2, 0]}>
        <mesh castShadow>
          <boxGeometry args={[0.62, 1.28, 0.25]} />
          <meshStandardMaterial color="#263c45" metalness={0.52} roughness={0.42} />
        </mesh>
        <mesh position={[0, 0.28, 0.135]}>
          <planeGeometry args={[0.4, 0.35]} />
          <meshBasicMaterial color="#74d6c1" />
        </mesh>
        <mesh position={[-0.14, -0.18, 0.145]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.06, 0.06, 0.025, 20]} />
          <meshStandardMaterial color="#f7c948" />
        </mesh>
        <mesh position={[0.14, -0.18, 0.145]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.07, 0.07, 0.025, 20]} />
          <meshStandardMaterial color="#e55f5f" />
        </mesh>
      </group>

      <group name="cnc-status-tower" position={[1.18, 3.08, -0.15]}>
        <mesh position={[0, 0.12, 0]}>
          <cylinderGeometry args={[0.055, 0.055, 0.24, 16]} />
          <meshStandardMaterial color="#82939a" metalness={0.7} />
        </mesh>
        <mesh position={[0, 0.3, 0]}>
          <cylinderGeometry args={[0.11, 0.11, 0.18, 20]} />
          <meshStandardMaterial color="#f7c948" emissive="#55420f" />
        </mesh>
      </group>
    </group>
  );
}
