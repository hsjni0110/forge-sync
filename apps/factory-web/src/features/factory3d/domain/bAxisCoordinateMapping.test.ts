import { describe, expect, it } from "vitest";

import { mapBaxisAngleToRotation, type BAxisCoordinateMapping } from "./bAxisCoordinateMapping";

const mapping: BAxisCoordinateMapping = {
  sourceDataItemId: "verified-b-axis",
  inputUnit: "DEGREE",
  rotationAxis: "z",
  sign: -1,
  zeroDegrees: 10,
  minimumDegrees: -90,
  maximumDegrees: 90,
  evidenceReference: "test-evidence",
};

describe("B-axis physical coordinate mapping", () => {
  it.each([
    [10, 0],
    [90, (-80 * Math.PI) / 180],
    [-90, (100 * Math.PI) / 180],
  ])("maps %s degrees with explicit zero and sign", (degrees, expected) => {
    expect(mapBaxisAngleToRotation(degrees, "DEGREE", mapping)).toEqual({
      availability: "AVAILABLE",
      radians: expected,
      rotationAxis: "z",
    });
  });

  it.each([[-90.001], [90.001], [Number.NaN]])("rejects rather than clamps %s", (degrees) => {
    expect(mapBaxisAngleToRotation(degrees, "DEGREE", mapping)).toEqual({
      availability: "UNAVAILABLE",
      reason: "OUT_OF_RANGE",
    });
  });

  it("rejects an unknown unit", () => {
    expect(mapBaxisAngleToRotation(45, "RADIAN", mapping)).toEqual({
      availability: "UNAVAILABLE",
      reason: "UNKNOWN_UNIT",
    });
  });
});
