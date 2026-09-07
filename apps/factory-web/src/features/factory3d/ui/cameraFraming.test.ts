import { Box3, Vector3 } from "three";
import { describe, expect, it } from "vitest";

import { calculateCameraFrame } from "./cameraFraming";

describe("camera framing policy", () => {
  it("frames different model sizes with the same proportional limits and padding", () => {
    const small = calculateCameraFrame(
      new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1)),
      42,
      16 / 9,
    );
    const large = calculateCameraFrame(
      new Box3(new Vector3(-4, 0, -4), new Vector3(4, 8, 4)),
      42,
      16 / 9,
    );

    expect(large.radius).toBeCloseTo(small.radius * 4);
    expect(large.distance).toBeCloseTo(small.distance * 4);
    expect(small.minDistance).toBeCloseTo(small.radius * 0.45);
    expect(small.maxDistance).toBeCloseTo(small.radius * 4);
    expect(small.visibleFraction).toBe(0.7);
  });

  it("lifts the machine clear of an overlay covering the lower viewport", () => {
    const bounds = new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1));

    const unobstructed = calculateCameraFrame(bounds, 42, 16 / 9);
    const overlaid = calculateCameraFrame(bounds, 42, 16 / 9, 0.4);

    // The subject has to sit above the overlay, so the aim point drops below the model centre.
    expect(overlaid.target.y).toBeLessThan(unobstructed.target.y);
    // And it has to fit in a shorter band, so the camera stands further back.
    expect(overlaid.distance).toBeGreaterThan(unobstructed.distance);
  });

  it("leaves framing untouched when nothing covers the viewport", () => {
    const bounds = new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1));

    const implicit = calculateCameraFrame(bounds, 42, 16 / 9);
    const explicitZero = calculateCameraFrame(bounds, 42, 16 / 9, 0);

    expect(explicitZero.target.toArray()).toEqual(implicit.target.toArray());
    expect(explicitZero.distance).toBeCloseTo(implicit.distance);
  });

  it("centers a selected part and keeps portrait layouts inside the viewport", () => {
    const frame = calculateCameraFrame(
      new Box3(new Vector3(2, 1, 4), new Vector3(4, 3, 8)),
      42,
      0.6,
    );

    expect(frame.target.toArray()).toEqual([3, 2, 6]);
    expect(frame.distance).toBeGreaterThan(frame.radius / Math.tan((42 * Math.PI) / 360));
  });
});
