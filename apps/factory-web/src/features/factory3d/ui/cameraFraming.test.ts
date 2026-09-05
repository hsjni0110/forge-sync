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
