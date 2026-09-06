import mappingDocument from "../../../../../../config/visualization/mazak01-observed-delta-mapping-v1.json";

import type {
  LinearAxis,
  LinearAxisCoordinateMapping,
} from "../domain/linearAxisCoordinateMapping";

export const MAZAK01_OBSERVED_DELTA_MAPPINGS: Record<
  LinearAxis,
  LinearAxisCoordinateMapping
> = {
  X: mappingFor("X"),
  Y: mappingFor("Y"),
  Z: mappingFor("Z"),
};

function mappingFor(axisName: LinearAxis): LinearAxisCoordinateMapping {
  const axis = mappingDocument.axes.find((candidate) => candidate.axis === axisName);
  if (!axis) throw new Error(`Observed delta mapping is missing ${axisName}`);
  return {
    axis: axisName,
    sourceDataItemId: axis.sourceDataItemId,
    inputUnit: "MILLIMETER" as const,
    sceneAxis: axis.sceneAxis as "x" | "y" | "z",
    directionSign: axis.directionSign as 1 | -1,
    referenceMillimeters: axis.referenceMillimeters,
    observedRangeMillimeters: axis.observedRangeMillimeters as [number, number],
    sceneUnitsPerMillimeter: axis.sceneUnitsPerMillimeter,
    classification: "OBSERVED_DELTA_MAPPING" as const,
    evidenceReference: axis.evidenceReference,
  };
}
