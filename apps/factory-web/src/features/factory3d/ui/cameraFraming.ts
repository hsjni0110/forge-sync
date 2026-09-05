import { Box3, Vector3 } from "three";

const VISIBLE_FRACTION = 0.7;

export interface CameraFrame {
  target: Vector3;
  radius: number;
  distance: number;
  minDistance: number;
  maxDistance: number;
  visibleFraction: number;
}

export function calculateCameraFrame(
  bounds: Box3,
  verticalFovDegrees: number,
  aspect: number,
): CameraFrame {
  const target = bounds.getCenter(new Vector3());
  const size = bounds.getSize(new Vector3());
  const radius = Math.max(size.length() / 2, 0.1);
  const verticalFovRadians = (verticalFovDegrees * Math.PI) / 180;
  const limitingHalfSize = Math.max(size.y / 2, size.x / (2 * aspect));
  const distance =
    limitingHalfSize / Math.tan(verticalFovRadians / 2) / VISIBLE_FRACTION +
    size.z / 2;

  return {
    target,
    radius,
    distance,
    minDistance: radius * 0.45,
    maxDistance: radius * 4,
    visibleFraction: VISIBLE_FRACTION,
  };
}
