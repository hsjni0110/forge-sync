import { useEffect, useMemo } from "react";
import { Box3, Box3Helper, BufferGeometry, Color, Line, LineBasicMaterial, Vector3 } from "three";

import type { SceneToolpath } from "../../toolpath/domain/observedToolpath";

const SIMULATED_TOOL_BASELINE: [number, number, number] = [0.55, 1.29, 0.55];

export function ObservedToolpathTrail({ toolpath }: { toolpath: SceneToolpath }) {
  const objects = useMemo(() => {
    const geometry = new BufferGeometry().setFromPoints(
      toolpath.points.map((point) => new Vector3(...point.position)),
    );
    const material = new LineBasicMaterial({ color: new Color("#78a9a2"), transparent: true, opacity: 0.82 });
    const trail = new Line(geometry, material);
    trail.name = "observed-toolpath-trail";
    trail.position.set(...SIMULATED_TOOL_BASELINE);
    const observedBox = new Box3(
      new Vector3(...toolpath.envelope.minimum),
      new Vector3(...toolpath.envelope.maximum),
    );
    const envelope = new Box3Helper(observedBox, new Color("#607f7b"));
    envelope.name = "observed-toolpath-envelope";
    envelope.position.set(...SIMULATED_TOOL_BASELINE);
    return { trail, envelope, geometry, material };
  }, [toolpath]);

  useEffect(() => () => {
    objects.geometry.dispose();
    objects.material.dispose();
    objects.envelope.geometry.dispose();
    const envelopeMaterials = Array.isArray(objects.envelope.material)
      ? objects.envelope.material
      : [objects.envelope.material];
    envelopeMaterials.forEach((material) => material.dispose());
  }, [objects]);

  return <>
    <primitive name="observed-toolpath-trail" object={objects.trail} />
    <primitive name="observed-toolpath-envelope" object={objects.envelope} />
  </>;
}
