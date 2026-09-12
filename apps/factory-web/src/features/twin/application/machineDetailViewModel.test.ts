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
    expect(detail.metrics).toContainEqual(expect.objectContaining({
      key: "b-axis",
      label: "B축 각도",
      value: "45°",
      availability: "AVAILABLE",
    }));
    expect(detail.provenance.map((item) => item.sourceDataItemId)).toContain("Mazak01-B_4");
    expect(detail.metrics).toContainEqual(expect.objectContaining({
      key: "axis-x",
      label: "X축 위치",
      value: "80.08 mm",
      availability: "AVAILABLE",
    }));
    expect(detail.provenance.map((item) => item.sourceDataItemId))
      .toEqual(expect.arrayContaining(["Mazak01-X_1", "Mazak01-Y_1", "Mazak01-Z_1"]));
  });

  it("shows each observed load and temperature channel under its own component and source", () => {
    const detail = mapTwinToMachineDetail(snapshotFixture());

    expect(detail.metrics).toContainEqual(
      expect.objectContaining({
        key: "load-Mazak01-X_3",
        label: "부하 · Mazak01-X",
        value: "4 %",
        availability: "AVAILABLE",
      }),
    );
    expect(detail.metrics).toContainEqual(
      expect.objectContaining({
        key: "load-Mazak01-Y_3",
        value: "확인할 수 없음",
        availability: "UNAVAILABLE",
      }),
    );
    expect(detail.metrics).toContainEqual(
      expect.objectContaining({
        key: "temp-Mazak01-C_7",
        label: "온도 · Mazak01-C",
        value: "31.2 °C",
      }),
    );
    expect(detail.provenance.map((item) => item.sourceDataItemId)).toEqual(
      expect.arrayContaining([
        "Mazak01-B_1",
        "Mazak01-C_2",
        "Mazak01-C2_1",
        "Mazak01-X_3",
        "Mazak01-Y_3",
        "Mazak01-Z_3",
        "Mazak01-C_7",
        "Mazak01-C2_3",
      ]),
    );
  });

  it("reads feedrate, part count, controller mode and power state from one snapshot", () => {
    const detail = mapTwinToMachineDetail(snapshotFixture());

    expect(detail.metrics).toContainEqual(
      expect.objectContaining({ key: "feedrate", label: "경로 이송속도", value: "5.4 mm/s" }),
    );
    expect(detail.metrics).toContainEqual(
      expect.objectContaining({ key: "part-count", label: "부품 수 카운터", value: "17" }),
    );
    expect(detail.metrics).toContainEqual(
      expect.objectContaining({ key: "controller-mode", label: "제어 모드", value: "자동" }),
    );
    expect(detail.metrics).toContainEqual(
      expect.objectContaining({ key: "power-state", label: "전원 상태", value: "켜짐" }),
    );
    expect(detail.provenance.map((item) => item.sourceDataItemId)).toEqual(
      expect.arrayContaining([
        "Mazak01-path_7",
        "Mazak01-path_6",
        "Mazak01-path_14",
        "Mazak01-electric_1",
      ]),
    );
  });

  it("renders a snapshot without the new channels exactly as it did before they existed", () => {
    const snapshot = snapshotFixture();
    delete snapshot.metrics.loads;
    delete snapshot.metrics.temperatures;
    delete snapshot.metrics.pathFeedrate;
    delete snapshot.metrics.partCount;
    delete snapshot.metrics.controllerMode;
    delete snapshot.metrics.powerState;

    const detail = mapTwinToMachineDetail(snapshot);

    expect(detail.metrics.map((metric) => metric.key)).toEqual([
      "spindle-Mazak01-C_5",
      "axis-x",
      "axis-y",
      "axis-z",
      "b-axis",
      "tool",
      "program",
    ]);
    expect(detail.metrics.map((metric) => metric.value)).not.toContain("0");
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

  it("distinguishes an observed tool zero from a missing tool", () => {
    const snapshot = snapshotFixture();
    if (!snapshot.metrics.toolNumber) throw new Error("fixture must include a tool number");
    snapshot.metrics.toolNumber.value = 0;

    const detail = mapTwinToMachineDetail(snapshot);

    expect(detail.metrics.find((metric) => metric.key === "tool")?.value).toBe(
      "0 · 미장착 여부 확인 불가",
    );
  });

  it("summarizes a single turning spindle without claiming the other channels' state", () => {
    const snapshot = snapshotFixture();
    snapshot.metrics.spindleSpeeds[0].value = 49;
    const detail = mapTwinToMachineDetail(snapshot);
    expect(detail.spindleSummary).toEqual({ value: "49 rpm", detail: "Mazak01-C 사용 중" });
  });

  it("summarizes every channel at rest as one stopped state instead of two zero tiles", () => {
    const snapshot = snapshotFixture();
    snapshot.metrics.spindleSpeeds[0].value = 0;
    const secondSpindle = structuredClone(snapshot.metrics.spindleSpeeds[0]);
    secondSpindle.observation.componentId = "Mazak01-C2";
    secondSpindle.value = 0;
    snapshot.metrics.spindleSpeeds.push(secondSpindle);

    const detail = mapTwinToMachineDetail(snapshot);
    expect(detail.spindleSummary).toEqual({ value: "0 rpm", detail: "Mazak01-C · Mazak01-C2 정지" });
  });

  it("does not assume only one channel can turn at a time", () => {
    const snapshot = snapshotFixture();
    snapshot.metrics.spindleSpeeds[0].value = 49;
    const secondSpindle = structuredClone(snapshot.metrics.spindleSpeeds[0]);
    secondSpindle.observation.componentId = "Mazak01-C2";
    secondSpindle.value = 125;
    snapshot.metrics.spindleSpeeds.push(secondSpindle);

    const detail = mapTwinToMachineDetail(snapshot);
    expect(detail.spindleSummary.value).toBe("2개 채널 동시 회전");
    expect(detail.spindleSummary.detail).toBe("Mazak01-C 49rpm · Mazak01-C2 125rpm");
  });

  it("picks the most severe active condition as the primary one without duplicating its provenance", () => {
    const snapshot = snapshotFixture();
    const template = snapshot.metrics.spindleSpeeds[0];
    snapshot.conditions = [
      { conditionType: "SYSTEM", level: "NORMAL", observation: template.observation, provenance: template.provenance },
      { conditionType: "LOGIC_PROGRAM", level: "WARNING", message: "ERROR(DOOR OPEN)", observation: template.observation, provenance: template.provenance },
      { conditionType: "HYDRAULIC", level: "FAULT", message: "압력 낮음", observation: template.observation, provenance: template.provenance },
    ];

    const detail = mapTwinToMachineDetail(snapshot);
    expect(detail.primaryCondition).toMatchObject({ conditionType: "HYDRAULIC", level: "고장" });
    expect(detail.provenance.filter((item) => item.field === "상태 신호 · HYDRAULIC")).toHaveLength(1);
  });

  it("has no primary condition when every signal is normal", () => {
    const snapshot = snapshotFixture();
    const template = snapshot.metrics.spindleSpeeds[0];
    snapshot.conditions = [
      { conditionType: "SYSTEM", level: "NORMAL", observation: template.observation, provenance: template.provenance },
    ];
    const detail = mapTwinToMachineDetail(snapshot);
    expect(detail.primaryCondition).toBeUndefined();
  });
});
