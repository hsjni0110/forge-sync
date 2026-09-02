import { describe, expect, it } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../domain/twin";
import { mapTwinToMachineDetail } from "./machineDetailViewModel";

function snapshotFixture(): TwinSnapshot {
  return structuredClone(twinFixture) as unknown as TwinSnapshot;
}

describe("mapTwinToMachineDetail", () => {
  it("keeps every spindle distinct and preserves field provenance", () => {
    const snapshot = snapshotFixture();
    const secondSpindle = structuredClone(snapshot.metrics.spindleSpeeds[0]);
    secondSpindle.observation.componentId = "Mazak01-C2";
    secondSpindle.provenance.transformation.sourceDataItemId = "Mazak01-C2_2";
    secondSpindle.value = 125;
    snapshot.metrics.spindleSpeeds.push(secondSpindle);

    const detail = mapTwinToMachineDetail(snapshot);

    expect(detail.metrics.map((metric) => metric.value)).toContain("49 rpm");
    expect(detail.metrics.map((metric) => metric.value)).toContain("125 rpm");
    expect(detail.provenance.map((item) => item.sourceLabel)).toContain(
      "실제 데이터 · NIST (REAL:NIST)",
    );
    expect(detail.provenance.map((item) => item.sourceDataItemId)).toContain("Mazak01-C2_2");
  });

  it("renders missing and unavailable optional metrics without inventing zero", () => {
    const snapshot = snapshotFixture();
    delete snapshot.metrics.toolNumber;
    if (snapshot.metrics.program) {
      snapshot.metrics.program.availability = "UNAVAILABLE";
      delete snapshot.metrics.program.value;
    }

    const detail = mapTwinToMachineDetail(snapshot);
    const tool = detail.metrics.find((metric) => metric.key === "tool");
    const program = detail.metrics.find((metric) => metric.key === "program");

    expect(tool).toMatchObject({ value: "확인할 수 없음", availability: "MISSING" });
    expect(program).toMatchObject({ value: "확인할 수 없음", availability: "UNAVAILABLE" });
    expect(detail.metrics.map((metric) => metric.value)).not.toContain("0");
  });
});
