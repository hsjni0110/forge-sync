import { describe, expect, it } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../../twin/domain/twin";
import { mapTwinToMachineVisualState } from "./twinToMachineVisualState";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

describe("mapTwinToMachineVisualState", () => {
  it("maps the configured visual spindle and shared Twin identity", () => {
    const visualState = mapTwinToMachineVisualState({
      snapshot,
      freshness: "FRESH",
      selectedMachineId: "Mazak01",
      visualSpindleSourceDataItemId: "Mazak01-C_5",
    });

    expect(visualState).toEqual({
      machineId: "Mazak01",
      twinVersion: 4,
      connectivity: "ONLINE",
      execution: "ACTIVE",
      health: "UNKNOWN",
      rpm: 49,
      rpmSourceDataItemId: "Mazak01-C_5",
      bAxisAngleDegrees: 45,
      bAxisAngleUnit: "DEGREE",
      bAxisAngleSourceDataItemId: "Mazak01-B_4",
      bAxisAngleSourceObservedAt: "2016-10-05T09:16:39.557Z",
      tool: "13",
      operationProgress: undefined,
      alarmSeverity: undefined,
      stale: false,
      selected: true,
    });
  });

  it("does not expose an unavailable B-axis observation as an angle", () => {
    const unavailable = structuredClone(snapshot);
    if (!unavailable.metrics.bAxisAngle) throw new Error("fixture requires B-axis angle");
    unavailable.metrics.bAxisAngle.availability = "UNAVAILABLE";
    delete unavailable.metrics.bAxisAngle.value;

    const visualState = mapTwinToMachineVisualState({
      snapshot: unavailable,
      freshness: "FRESH",
      selectedMachineId: "Mazak01",
      visualSpindleSourceDataItemId: "Mazak01-C_5",
    });

    expect(visualState.bAxisAngleDegrees).toBeUndefined();
    expect(visualState.bAxisAngleSourceDataItemId).toBeUndefined();
  });

  it("does not guess another spindle or fabricate optional values", () => {
    const unavailable = structuredClone(snapshot);
    unavailable.metrics.spindleSpeeds[0].availability = "UNAVAILABLE";
    delete unavailable.metrics.spindleSpeeds[0].value;
    delete unavailable.metrics.toolNumber;

    const visualState = mapTwinToMachineVisualState({
      snapshot: unavailable,
      freshness: "STALE",
      selectedMachineId: undefined,
      visualSpindleSourceDataItemId: "Mazak01-C_5",
    });

    expect(visualState.rpm).toBeUndefined();
    expect(visualState.rpmSourceDataItemId).toBe("Mazak01-C_5");
    expect(visualState.tool).toBeUndefined();
    expect(visualState.connectivity).toBe("STALE");
    expect(visualState.stale).toBe(true);
    expect(visualState.selected).toBe(false);
  });

  it("leaves rpm unavailable when the configured DataItem is absent", () => {
    const secondSpindle = structuredClone(snapshot.metrics.spindleSpeeds[0]);
    secondSpindle.value = 2_000;
    secondSpindle.provenance.transformation.sourceDataItemId = "Mazak01-C2_2";

    const visualState = mapTwinToMachineVisualState({
      snapshot: { ...snapshot, metrics: { ...snapshot.metrics, spindleSpeeds: [secondSpindle] } },
      freshness: "FRESH",
      selectedMachineId: "Mazak01",
      visualSpindleSourceDataItemId: "Mazak01-C_5",
    });

    expect(visualState.rpm).toBeUndefined();
    expect(visualState.rpmSourceDataItemId).toBe("Mazak01-C_5");
  });
});
