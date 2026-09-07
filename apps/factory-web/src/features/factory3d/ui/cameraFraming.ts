import { Box3, Vector3 } from "three";

/**
 * Share of the limiting viewport axis the machine fills. Orthographic framing has no perspective
 * falloff to imply space, so the margin can be tighter than a perspective camera wants before the
 * machine starts to feel cramped.
 */
const VISIBLE_FRACTION = 0.82;

export interface IsometricFrame {
  target: Vector3;
  radius: number;
  distance: number;
  zoom: number;
  minZoom: number;
  maxZoom: number;
  screenLift: number;
  visibleFraction: number;
}

/**
 * The scene uses an orthographic isometric camera, so framing sets a zoom rather than a distance:
 * equal travel measures equal on screen wherever the tool is, which is what makes an observed
 * toolpath comparable. `zoom` is world units per pixel inverted — the frustum is the canvas in
 * pixels at zoom 1, the convention react-three-fiber sets up for an orthographic canvas.
 *
 * `obstructedBottomFraction` is the share of viewport height hidden behind overlays along the
 * bottom edge. The machine has to read inside what is left, so it fits a shorter band and
 * `screenLift` says how far the aim point moves down-screen to raise it clear. The caller applies
 * that along the camera's own up vector, which world Y only approximates at an isometric angle.
 */
export function calculateIsometricFrame(
  bounds: Box3,
  viewportWidth: number,
  viewportHeight: number,
  obstructedBottomFraction = 0,
): IsometricFrame {
  const obstructed = Math.min(Math.max(obstructedBottomFraction, 0), 0.8);
  const clearFraction = 1 - obstructed;
  const target = bounds.getCenter(new Vector3());
  const size = bounds.getSize(new Vector3());
  const radius = Math.max(size.length() / 2, 0.1);
  // An isometric camera turns depth into screen width and height, so the diagonal is what has to
  // fit, not the axis-aligned width.
  const projectedWidth = Math.max(size.x + size.z, 0.1);
  const projectedHeight = Math.max(size.y + (size.x + size.z) / 2, 0.1);
  const clearHeight = Math.max(viewportHeight * clearFraction, 1);
  const zoom =
    Math.min(viewportWidth / projectedWidth, clearHeight / projectedHeight) * VISIBLE_FRACTION;

  return {
    target,
    radius,
    // Orthographic scale is set by zoom, so distance only has to keep the model off the near plane.
    distance: radius * 4,
    zoom,
    minZoom: zoom * 0.35,
    maxZoom: zoom * 4,
    screenLift: (viewportHeight * obstructed) / 2 / zoom,
    visibleFraction: VISIBLE_FRACTION,
  };
}
