export interface BAxisCoordinateMapping {
  readonly sourceDataItemId: string;
  readonly inputUnit: "DEGREE";
  readonly rotationAxis: "x" | "y" | "z";
  readonly sign: 1 | -1;
  readonly zeroDegrees: number;
  readonly minimumDegrees: number;
  readonly maximumDegrees: number;
  readonly evidenceReference: string;
}

export type BAxisRotation =
  | { availability: "AVAILABLE"; radians: number; rotationAxis: "x" | "y" | "z" }
  | { availability: "UNAVAILABLE"; reason: "UNKNOWN_UNIT" | "OUT_OF_RANGE" };

export function mapBaxisAngleToRotation(
  degrees: number,
  unit: string | undefined,
  mapping: BAxisCoordinateMapping,
): BAxisRotation {
  if (unit !== mapping.inputUnit) {
    return { availability: "UNAVAILABLE", reason: "UNKNOWN_UNIT" };
  }
  if (
    !Number.isFinite(degrees) ||
    degrees < mapping.minimumDegrees ||
    degrees > mapping.maximumDegrees
  ) {
    return { availability: "UNAVAILABLE", reason: "OUT_OF_RANGE" };
  }
  const radians = mapping.sign * (degrees - mapping.zeroDegrees) * (Math.PI / 180);
  return {
    availability: "AVAILABLE",
    radians: Object.is(radians, -0) ? 0 : radians,
    rotationAxis: mapping.rotationAxis,
  };
}
