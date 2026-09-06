import { describe, expect, it } from "vitest";

import type { LinearAxisCoordinateMapping } from "../../factory3d/domain/linearAxisCoordinateMapping";
import { mapObservedToolpathToScene } from "./observedToolpath";

const mappings = {
  X: mapping("X", "y", -0.00127),
  Y: mapping("Y", "x", 0),
  Z: mapping("Z", "z", 9.635617),
};

describe("observed toolpath scene mapping", () => {
  it("maps source points without interpolation and derives the observed scene envelope", () => {
    const path = mapObservedToolpathToScene({
      schemaVersion: "1.0.0",
      machineId: "Mazak01",
      replaySessionId: "session-1",
      startSequence: 10,
      endSequence: 12,
      availability: "AVAILABLE",
      classification: "OBSERVED_PATH",
      points: [point(10, [-0.00127, 0, 9.635617]), point(12, [10, -5, 19.635617])],
      observedEnvelope: {
        minimumMillimeters: [-0.00127, -5, 9.635617],
        maximumMillimeters: [10, 0, 19.635617],
      },
    }, mappings);

    expect(path?.points.map((point) => point.replaySequence)).toEqual([10, 12]);
    expect(path?.points[0].position).toEqual([0, 0, 0]);
    expect(path?.points[1].position[0]).toBeCloseTo(-0.006);
    expect(path?.points[1].position[1]).toBeCloseTo(0.012001524);
    expect(path?.points[1].position[2]).toBeCloseTo(0.012);
    expect(path?.envelope.minimum[0]).toBeCloseTo(-0.006);
    expect(path?.envelope.maximum[1]).toBeCloseTo(0.012001524);
    expect(path?.envelope.maximum[2]).toBeCloseTo(0.012);
  });

  it("does not create a partial path when an axis mapping is unavailable", () => {
    const partialMappings: Partial<typeof mappings> = { ...mappings };
    delete partialMappings.Z;
    expect(mapObservedToolpathToScene({
      schemaVersion: "1.0.0", machineId: "Mazak01", replaySessionId: "session-1",
      startSequence: 1, endSequence: 1, availability: "AVAILABLE", classification: "OBSERVED_PATH",
      points: [point(1, [0, 0, 0])],
      observedEnvelope: { minimumMillimeters: [0, 0, 0], maximumMillimeters: [0, 0, 0] },
    }, partialMappings)).toBeUndefined();
  });
});

function point(replaySequence: number, coordinatesMillimeters: number[]) {
  return {
    replaySequence,
    sourceObservedAt: "2016-10-05T09:00:00Z",
    coordinatesMillimeters,
    sourceObservations: [],
  };
}

function mapping(
  axis: "X" | "Y" | "Z",
  sceneAxis: "x" | "y" | "z",
  referenceMillimeters: number,
): LinearAxisCoordinateMapping {
  return {
    axis,
    sourceDataItemId: `Mazak01-${axis}_1`,
    inputUnit: "MILLIMETER",
    sceneAxis,
    directionSign: 1,
    referenceMillimeters,
    observedRangeMillimeters: [-1000, 1000],
    sceneUnitsPerMillimeter: 0.0012,
    classification: "OBSERVED_DELTA_MAPPING",
    evidenceReference: "test fixture",
  };
}
