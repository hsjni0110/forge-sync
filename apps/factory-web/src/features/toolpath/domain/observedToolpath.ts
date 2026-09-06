import {
  mapObservedAxisDelta,
  type LinearAxis,
  type LinearAxisCoordinateMapping,
} from "../../factory3d/domain/linearAxisCoordinateMapping";

export interface ObservedToolpathDocument {
  schemaVersion: "1.0.0";
  machineId: string;
  replaySessionId: string;
  startSequence: number;
  endSequence: number;
  availability: "AVAILABLE" | "UNAVAILABLE";
  reason?: string;
  classification: "OBSERVED_PATH";
  points: Array<{
    replaySequence: number;
    sourceObservedAt: string;
    coordinatesMillimeters: number[];
    sourceObservations: unknown[];
  }>;
  observedEnvelope?: { minimumMillimeters: number[]; maximumMillimeters: number[] };
}

export interface SceneToolpath {
  points: Array<{ replaySequence: number; position: [number, number, number] }>;
  envelope: { minimum: [number, number, number]; maximum: [number, number, number] };
}

export function mapObservedToolpathToScene(
  document: ObservedToolpathDocument,
  mappings: Partial<Record<LinearAxis, LinearAxisCoordinateMapping>>,
): SceneToolpath | undefined {
  if (document.availability !== "AVAILABLE" || document.points.length === 0) return undefined;
  const axes: LinearAxis[] = ["X", "Y", "Z"];
  if (axes.some((axis) => mappings[axis] === undefined)) return undefined;
  const points = document.points.map((point) => {
    if (point.coordinatesMillimeters.length !== 3) return undefined;
    const position: [number, number, number] = [0, 0, 0];
    for (let index = 0; index < axes.length; index += 1) {
      const mapping = mappings[axes[index]]!;
      const mapped = mapObservedAxisDelta(
        point.coordinatesMillimeters[index], mapping.inputUnit, mapping.sourceDataItemId, mapping,
      );
      if (mapped.availability !== "AVAILABLE") return undefined;
      position[sceneIndex(mapped.sceneAxis)] = mapped.offsetSceneUnits;
    }
    return { replaySequence: point.replaySequence, position };
  });
  if (points.some((point) => point === undefined)) return undefined;
  const complete = points as SceneToolpath["points"];
  return { points: complete, envelope: envelope(complete.map((point) => point.position)) };
}

function sceneIndex(axis: "x" | "y" | "z"): 0 | 1 | 2 {
  return axis === "x" ? 0 : axis === "y" ? 1 : 2;
}

function envelope(points: Array<[number, number, number]>) {
  const minimum: [number, number, number] = [...points[0]];
  const maximum: [number, number, number] = [...points[0]];
  for (const point of points) {
    for (let index = 0; index < 3; index += 1) {
      minimum[index] = Math.min(minimum[index], point[index]);
      maximum[index] = Math.max(maximum[index], point[index]);
    }
  }
  return { minimum, maximum };
}
