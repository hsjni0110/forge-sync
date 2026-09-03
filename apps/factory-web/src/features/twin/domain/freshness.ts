import type { Freshness, TwinSnapshot } from "./twin";

export function effectiveAgeMillis(snapshot: TwinSnapshot, nowMillis: number): number {
  const evaluatedAtMillis = Date.parse(snapshot.state.freshness.evaluatedAt);
  const elapsedAgeMillis =
    snapshot.state.freshness.ageMillis + Math.max(0, nowMillis - evaluatedAtMillis);
  return Math.max(elapsedAgeMillis, minimumAgeForDeclaredFreshness(snapshot));
}

function minimumAgeForDeclaredFreshness(snapshot: TwinSnapshot): number {
  switch (snapshot.state.freshness.value) {
    case "FRESH":
      return 0;
    case "LAGGING":
      return snapshot.state.freshness.freshMaxAgeMillis + 1;
    case "STALE":
      return snapshot.state.freshness.laggingMaxAgeMillis + 1;
  }
}

export function classifyFreshness(snapshot: TwinSnapshot, nowMillis: number): Freshness {
  const ageMillis = effectiveAgeMillis(snapshot, nowMillis);
  if (ageMillis <= snapshot.state.freshness.freshMaxAgeMillis) {
    return "FRESH";
  }
  if (ageMillis <= snapshot.state.freshness.laggingMaxAgeMillis) {
    return "LAGGING";
  }
  return "STALE";
}

export function nextFreshnessBoundaryMillis(
  snapshot: TwinSnapshot,
  nowMillis: number,
): number | undefined {
  const ageMillis = effectiveAgeMillis(snapshot, nowMillis);
  if (ageMillis <= snapshot.state.freshness.freshMaxAgeMillis) {
    return snapshot.state.freshness.freshMaxAgeMillis - ageMillis + 1;
  }
  if (ageMillis <= snapshot.state.freshness.laggingMaxAgeMillis) {
    return snapshot.state.freshness.laggingMaxAgeMillis - ageMillis + 1;
  }
  return undefined;
}

export function effectiveConsistency(
  snapshot: TwinSnapshot,
  freshness: Freshness,
): TwinSnapshot["consistency"]["status"] {
  return freshness === "STALE" ? "STALE" : snapshot.consistency.status;
}

export function effectiveConnectivity(
  snapshot: TwinSnapshot,
  freshness: Freshness,
): TwinSnapshot["state"]["connectivity"]["value"] {
  return freshness === "STALE" && snapshot.state.connectivity.value === "ONLINE"
    ? "STALE"
    : snapshot.state.connectivity.value;
}
