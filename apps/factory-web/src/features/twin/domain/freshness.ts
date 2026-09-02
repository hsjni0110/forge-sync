import type { Freshness, TwinSnapshot } from "./twin";

const FRESH_MAX_AGE_MILLIS = 2_000;
const LAGGING_MAX_AGE_MILLIS = 10_000;

export function effectiveAgeMillis(snapshot: TwinSnapshot, nowMillis: number): number {
  const evaluatedAtMillis = Date.parse(snapshot.state.freshness.evaluatedAt);
  return (
    snapshot.state.freshness.ageMillis + Math.max(0, nowMillis - evaluatedAtMillis)
  );
}

export function classifyFreshness(snapshot: TwinSnapshot, nowMillis: number): Freshness {
  const ageMillis = effectiveAgeMillis(snapshot, nowMillis);
  if (ageMillis <= FRESH_MAX_AGE_MILLIS) {
    return "FRESH";
  }
  if (ageMillis <= LAGGING_MAX_AGE_MILLIS) {
    return "LAGGING";
  }
  return "STALE";
}

export function nextFreshnessBoundaryMillis(
  snapshot: TwinSnapshot,
  nowMillis: number,
): number | undefined {
  const ageMillis = effectiveAgeMillis(snapshot, nowMillis);
  if (ageMillis <= FRESH_MAX_AGE_MILLIS) {
    return FRESH_MAX_AGE_MILLIS - ageMillis + 1;
  }
  if (ageMillis <= LAGGING_MAX_AGE_MILLIS) {
    return LAGGING_MAX_AGE_MILLIS - ageMillis + 1;
  }
  return undefined;
}
