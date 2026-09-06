export type LinearAxis = "X" | "Y" | "Z";

export interface LinearAxisCoordinateMapping {
  axis: LinearAxis;
  sourceDataItemId: string;
  inputUnit: "MILLIMETER";
  sceneAxis: "x" | "y" | "z";
  directionSign: 1 | -1;
  referenceMillimeters: number;
  observedRangeMillimeters: readonly [number, number];
  sceneUnitsPerMillimeter: number;
  classification: "OBSERVED_DELTA_MAPPING";
  evidenceReference: string;
}

export type LinearAxisTranslation =
  | { availability: "AVAILABLE"; axis: LinearAxis; sceneAxis: "x" | "y" | "z"; offsetSceneUnits: number }
  | { availability: "UNAVAILABLE"; reason: string };

export function mapObservedAxisDelta(
  value: number,
  unit: string,
  sourceDataItemId: string,
  mapping: LinearAxisCoordinateMapping,
): LinearAxisTranslation {
  if (unit !== mapping.inputUnit) {
    return { availability: "UNAVAILABLE", reason: "지원하지 않는 위치 단위" };
  }
  if (sourceDataItemId !== mapping.sourceDataItemId) {
    return { availability: "UNAVAILABLE", reason: "좌표 매핑과 다른 원본 항목" };
  }
  if (!Number.isFinite(value)) {
    return { availability: "UNAVAILABLE", reason: "유효하지 않은 위치 값" };
  }
  const [minimum, maximum] = mapping.observedRangeMillimeters;
  if (value < minimum || value > maximum) {
    return { availability: "UNAVAILABLE", reason: "검증된 관측 데이터 범위 밖" };
  }
  return {
    availability: "AVAILABLE",
    axis: mapping.axis,
    sceneAxis: mapping.sceneAxis,
    offsetSceneUnits:
      (value - mapping.referenceMillimeters) *
      mapping.directionSign *
      mapping.sceneUnitsPerMillimeter,
  };
}
