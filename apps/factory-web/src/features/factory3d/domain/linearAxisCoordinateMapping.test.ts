import { describe, expect, it } from "vitest";

import {
  mapObservedAxisDelta,
  type LinearAxisCoordinateMapping,
} from "./linearAxisCoordinateMapping";

const mapping: LinearAxisCoordinateMapping = {
  axis: "X",
  sourceDataItemId: "Mazak01-X_1",
  inputUnit: "MILLIMETER",
  sceneAxis: "y",
  directionSign: -1,
  referenceMillimeters: 10,
  observedRangeMillimeters: [-20, 30],
  sceneUnitsPerMillimeter: 0.01,
  classification: "OBSERVED_DELTA_MAPPING",
  evidenceReference: "nist-device-and-source-anchor",
};

describe("observed linear-axis delta mapping", () => {
  it("keeps the authored pose at the source anchor and maps signed deltas", () => {
    expect(mapObservedAxisDelta(10, "MILLIMETER", "Mazak01-X_1", mapping)).toEqual({
      availability: "AVAILABLE", axis: "X", sceneAxis: "y", offsetSceneUnits: -0,
    });
    expect(mapObservedAxisDelta(30, "MILLIMETER", "Mazak01-X_1", mapping)).toEqual({
      availability: "AVAILABLE", axis: "X", sceneAxis: "y", offsetSceneUnits: -0.2,
    });
    expect(mapObservedAxisDelta(-20, "MILLIMETER", "Mazak01-X_1", mapping)).toEqual({
      availability: "AVAILABLE", axis: "X", sceneAxis: "y", offsetSceneUnits: 0.3,
    });
  });

  it("rejects unknown identity, unit, non-finite values, and values outside the observed source range", () => {
    expect(mapObservedAxisDelta(10, "INCH", "Mazak01-X_1", mapping).availability).toBe("UNAVAILABLE");
    expect(mapObservedAxisDelta(10, "MILLIMETER", "other", mapping).availability).toBe("UNAVAILABLE");
    expect(mapObservedAxisDelta(Number.NaN, "MILLIMETER", "Mazak01-X_1", mapping).availability).toBe("UNAVAILABLE");
    expect(mapObservedAxisDelta(31, "MILLIMETER", "Mazak01-X_1", mapping).availability).toBe("UNAVAILABLE");
  });
});
