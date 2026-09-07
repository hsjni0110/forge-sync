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

/**
 * `obstructedBottomFraction` is the share of the viewport height hidden behind overlays along the
 * bottom edge. The machine has to read inside what is left, so it must fit a shorter band and sit
 * above the overlays rather than behind them.
 */
export function calculateCameraFrame(
  bounds: Box3,
  verticalFovDegrees: number,
  aspect: number,
  obstructedBottomFraction = 0,
): CameraFrame {
  const obstructed = Math.min(Math.max(obstructedBottomFraction, 0), 0.8);
  const clearFraction = 1 - obstructed;
  const target = bounds.getCenter(new Vector3());
  const size = bounds.getSize(new Vector3());
  const radius = Math.max(size.length() / 2, 0.1);
  const verticalFovRadians = (verticalFovDegrees * Math.PI) / 180;
  const limitingHalfSize = Math.max(size.y / (2 * clearFraction), size.x / (2 * aspect));
  const distance =
    limitingHalfSize / Math.tan(verticalFovRadians / 2) / VISIBLE_FRACTION +
    size.z / 2;
  // Aiming below the model centre by half the hidden band raises the machine into the clear part.
  target.y -= distance * Math.tan(verticalFovRadians / 2) * obstructed;

  return {
    target,
    radius,
    distance,
    minDistance: radius * 0.45,
    maxDistance: radius * 4,
    visibleFraction: VISIBLE_FRACTION,
  };
}
