import { Box3, Vector3 } from "three";
import { describe, expect, it } from "vitest";

import { calculateIsometricFrame } from "./cameraFraming";

const VIEWPORT = { width: 900, height: 500 };

describe("isometric framing policy", () => {
  it("scales zoom inversely with the model so every machine fills the same share", () => {
    const small = calculateIsometricFrame(
      new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1)),
      VIEWPORT.width,
      VIEWPORT.height,
    );
    const large = calculateIsometricFrame(
      new Box3(new Vector3(-4, 0, -4), new Vector3(4, 8, 4)),
      VIEWPORT.width,
      VIEWPORT.height,
    );

    expect(small.zoom).toBeCloseTo(large.zoom * 4);
    expect(small.minZoom).toBeLessThan(small.zoom);
    expect(small.maxZoom).toBeGreaterThan(small.zoom);
  });

  it("aims at the model centre and asks for no lift when nothing covers the viewport", () => {
    const frame = calculateIsometricFrame(
      new Box3(new Vector3(2, 1, 4), new Vector3(4, 3, 8)),
      VIEWPORT.width,
      VIEWPORT.height,
    );

    expect(frame.target.toArray()).toEqual([3, 2, 6]);
    expect(frame.screenLift).toBe(0);
  });

  it("pulls back and lifts the subject when an overlay covers the lower viewport", () => {
    const bounds = new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1));

    const clear = calculateIsometricFrame(bounds, VIEWPORT.width, VIEWPORT.height);
    const covered = calculateIsometricFrame(bounds, VIEWPORT.width, VIEWPORT.height, 0.4);

    expect(covered.zoom).toBeLessThan(clear.zoom);
    expect(covered.screenLift).toBeGreaterThan(0);
    expect(covered.target.toArray()).toEqual(clear.target.toArray());
  });

  it("keeps a wide model inside a narrow viewport by fitting the limiting axis", () => {
    const wide = new Box3(new Vector3(-10, 0, -1), new Vector3(10, 2, 1));

    const frame = calculateIsometricFrame(wide, 400, 900);

    expect(frame.zoom * 20).toBeLessThanOrEqual(400);
  });

  it("stands the camera clear of the model so nothing crosses the near plane", () => {
    const bounds = new Box3(new Vector3(-1, 0, -1), new Vector3(1, 2, 1));

    const frame = calculateIsometricFrame(bounds, VIEWPORT.width, VIEWPORT.height);

    expect(frame.distance).toBeGreaterThan(frame.radius);
  });
});
