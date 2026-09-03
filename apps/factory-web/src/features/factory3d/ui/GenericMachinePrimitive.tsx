export function GenericMachinePrimitive() {
  return (
    <group name="generic-cnc-primitive">
      <mesh position={[0, 1.25, 0]} castShadow receiveShadow>
        <boxGeometry args={[2.8, 2.5, 2.2]} />
        <meshStandardMaterial color="#315563" metalness={0.55} roughness={0.5} />
      </mesh>
      <mesh position={[0, 1.45, 1.12]} castShadow>
        <boxGeometry args={[1.5, 1.1, 0.12]} />
        <meshStandardMaterial color="#15242b" metalness={0.35} roughness={0.35} />
      </mesh>
      <mesh position={[1.05, 1.5, 1.2]} castShadow>
        <boxGeometry args={[0.3, 0.65, 0.16]} />
        <meshStandardMaterial color="#83e6cb" emissive="#193d35" />
      </mesh>
      <mesh position={[-0.95, 0.2, 0]} castShadow receiveShadow>
        <boxGeometry args={[0.55, 0.4, 2]} />
        <meshStandardMaterial color="#20363f" metalness={0.5} roughness={0.6} />
      </mesh>
      <mesh position={[0.95, 0.2, 0]} castShadow receiveShadow>
        <boxGeometry args={[0.55, 0.4, 2]} />
        <meshStandardMaterial color="#20363f" metalness={0.5} roughness={0.6} />
      </mesh>
    </group>
  );
}
