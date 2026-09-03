import { describe, expect, it } from "vitest";

import operationalBaseline from "../../../../../../tests/fixtures/regression/mazak01-operational-baseline.json";
import type { MachineVisualState } from "./machineVisualState";
import {
  deriveMachineVisualPresentation,
  nextVisualSpindleRotation,
  normalizeVisualSpindleSpeed,
} from "./machineVisualPresentation";

describe("Machine visual presentation policy", () => {
  it("keeps the frozen operational visual meaning", () => {
    const expected = operationalBaseline.expectedOperationalState;
    const active = presentation({
      machineId: expected.machineId,
      twinVersion: expected.twinVersion,
      execution: expected.execution as MachineVisualState["execution"],
      rpm: expected.rpm,
      rpmSourceDataItemId: expected.visualSpindleSourceDataItemId,
      tool: String(expected.toolNumber),
    });

    expect(active.visualAngularVelocityRadPerSec).toBe(
      expected.visualAngularVelocityRadPerSecond,
    );
    expect(active.isSpindleAnimating).toBe(true);
    expect(presentation({ ...activeVisualState(expected), stale: true }).isSpindleAnimating).toBe(
      false,
    );
  });

  it("normalizes RPM without claiming physical angular velocity", () => {
    expect(normalizeVisualSpindleSpeed(undefined)).toBe(0);
    expect(normalizeVisualSpindleSpeed(0)).toBe(0);
    expect(normalizeVisualSpindleSpeed(-1)).toBe(0);
    expect(normalizeVisualSpindleSpeed(49)).toBeCloseTo(0.098);
    expect(normalizeVisualSpindleSpeed(1_873)).toBeCloseTo(3.746);
    expect(normalizeVisualSpindleSpeed(6_842)).toBe(4);
  });

  it("animates only an online, fresh, active machine with positive RPM", () => {
    expect(presentation().isSpindleAnimating).toBe(true);
    expect(presentation({ rpm: 0 }).isSpindleAnimating).toBe(false);
    expect(presentation({ execution: "STOPPED" }).isSpindleAnimating).toBe(false);
    expect(presentation({ execution: "HOLD" }).isSpindleAnimating).toBe(false);
    expect(presentation({ connectivity: "OFFLINE" }).isSpindleAnimating).toBe(false);
    expect(presentation({ stale: true }).isSpindleAnimating).toBe(false);
    expect(presentation({}, true).isSpindleAnimating).toBe(false);
  });

  it("uses safety-first status priority while keeping selection independent", () => {
    expect(presentation({ stale: true, health: "FAULT" }).status).toBe("STALE");
    expect(presentation({ connectivity: "OFFLINE", health: "FAULT" }).status).toBe(
      "OFFLINE",
    );
    expect(presentation({ health: "FAULT" }).status).toBe("FAULT");
    expect(presentation({ health: "WARNING" }).status).toBe("WARNING");
    expect(presentation({ connectivity: "UNKNOWN" }).status).toBe("UNKNOWN");
    expect(presentation({ execution: "HOLD" }).status).toBe("HOLD");
    expect(presentation().status).toBe("ACTIVE");
    expect(presentation({ execution: "STOPPED" }).status).toBe("STOPPED");
    expect(presentation({ execution: "UNKNOWN" }).status).toBe("UNKNOWN");
  });

  it("removes nonessential motion but preserves warning meaning", () => {
    const warning = presentation({ health: "WARNING" }, true);

    expect(warning.statusLabel).toBe("주의");
    expect(warning.statusSymbol).toBe("▲");
    expect(warning.isBeaconPulsing).toBe(false);
    expect(warning.isSpindleAnimating).toBe(false);
    expect(warning.materialTone).toBe("WARNING");
  });

  it("advances frames only while visual spindle animation is allowed", () => {
    const active = presentation();
    const stopped = presentation({ execution: "STOPPED" });
    const afterActiveFrame = nextVisualSpindleRotation(1, active, 0.5);

    expect(afterActiveFrame).toBe(2);
    expect(nextVisualSpindleRotation(afterActiveFrame, stopped, 0.5)).toBe(2);
  });
});

function activeVisualState(
  expected: typeof operationalBaseline.expectedOperationalState,
): Partial<MachineVisualState> {
  return {
    machineId: expected.machineId,
    twinVersion: expected.twinVersion,
    execution: expected.execution as MachineVisualState["execution"],
    rpm: expected.rpm,
    rpmSourceDataItemId: expected.visualSpindleSourceDataItemId,
    tool: String(expected.toolNumber),
  };
}

function presentation(
  overrides: Partial<MachineVisualState> = {},
  isReducedMotion = false,
) {
  return deriveMachineVisualPresentation(
    {
      machineId: "Mazak01",
      twinVersion: 4,
      connectivity: "ONLINE",
      execution: "ACTIVE",
      health: "NORMAL",
      rpm: 1_000,
      rpmSourceDataItemId: "Mazak01-C_5",
      stale: false,
      selected: true,
      ...overrides,
    },
    isReducedMotion,
  );
}
