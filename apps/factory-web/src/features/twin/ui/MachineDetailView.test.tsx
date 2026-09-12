import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../domain/twin";
import { MachineDetailView } from "./MachineDetailView";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

afterEach(cleanup);

describe("MachineDetailView", () => {
  it("renders every P0 section, metric, freshness, and provenance", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getByRole("heading", { name: "Mazak01", level: 1 })).toBeTruthy();
    for (const section of [
      "지금 작업",
      "기본 정보",
      "현재 상태",
      "측정값",
      "상태 신호",
      "데이터 품질",
      "데이터 출처",
    ]) {
      expect(screen.getByRole("heading", { name: section })).toBeTruthy();
    }
    const hero = screen.getByRole("region", { name: "설비 가동 상태" });
    expect(within(hero).getByText("가동 중")).toBeTruthy();
    expect(screen.getAllByText("49 rpm")).toHaveLength(2);
    expect(screen.getByText("13")).toBeTruthy();
    expect(screen.getByText("114")).toBeTruthy();
    expect(screen.getAllByText(/실제 데이터 · NIST/).length).toBeGreaterThan(0);
    expect(screen.getByText(/상세 품질 정보는 아직 제공하지 않습니다/)).toBeTruthy();
    expect(screen.getByText(/원본 추적 정보 20건 · 1개 출처/)).toBeTruthy();
  });

  it("shows the newly exposed channels in the section that matches how they are read", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
      />,
    );

    const currentWork = screen.getByRole("region", { name: "지금 작업" });
    expect(within(currentWork).getByText("자동")).toBeTruthy();
    expect(within(currentWork).getByText("켜짐")).toBeTruthy();
    expect(within(currentWork).getByText("17")).toBeTruthy();

    const readings = screen.getByRole("region", { name: "측정값" });
    expect(within(readings).getByText("부하 · Mazak01-X")).toBeTruthy();
    expect(within(readings).getByText("4 %")).toBeTruthy();
    expect(within(readings).getByText("온도 · Mazak01-C2")).toBeTruthy();
    expect(within(readings).getByText("29.8 °C")).toBeTruthy();
    expect(within(readings).getByText("5.4 mm/s")).toBeTruthy();
    const unavailableLoad = within(readings)
      .getByText("부하 · Mazak01-Y")
      .closest<HTMLElement>(".metric-card");
    expect(within(unavailableLoad!).getByText("확인할 수 없음")).toBeTruthy();
    expect(within(readings).getByText("0 %")).toBeTruthy();
  });

  it("renders a compact operational summary without the full provenance list", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
        layout="COMPACT"
      />,
    );

    expect(screen.getByRole("heading", { name: "Mazak01", level: 2 })).toBeTruthy();
    expect(screen.getByText("49 rpm")).toBeTruthy();
    expect(screen.getByText("가동 중")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "데이터 출처" })).toBeNull();
    expect(screen.queryByRole("link", { name: "전체 설비 상세 보기" })).toBeNull();
    expect(screen.getByText(/상단 2D 보기에서 전체 상세/)).toBeTruthy();
  });

  it("keeps values visible while reconnecting and warns when stale", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "RECONNECTING", snapshot, freshness: "STALE" }}
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getAllByText("49 rpm")).toHaveLength(2);
    expect(screen.getByText(/마지막으로 받은 값을 표시합니다/)).toBeTruthy();
    expect(screen.getByRole("alert").textContent).toMatch(/실시간 상태로 판단하지 마세요/);
    expect(screen.getAllByText("오래된 데이터").length).toBeGreaterThanOrEqual(3);
  });

  it("shows completed replay without presenting its stopped data as a fault", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot, freshness: "STALE" }}
        replayStatus="COMPLETED"
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getByRole("status").textContent).toMatch(/재생이 완료되었습니다/);
    expect(screen.getByText("마지막 재생 데이터")).toBeTruthy();
    expect(screen.queryByText(/실시간 상태로 판단하지 마세요/)).toBeNull();
    expect(screen.queryByText("오래된 데이터")).toBeNull();
  });

  it("groups provenance by source and keeps the collection collapsed initially", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
      />,
    );

    const disclosure = screen.getByText(/원본 추적 정보 20건 · 1개 출처/).closest("details");
    expect(disclosure?.hasAttribute("open")).toBe(false);
    expect(within(disclosure!).getByRole("heading", { name: /NIST.*20건/ })).toBeTruthy();
  });

  it("summarizes many condition signals instead of listing every normal one", () => {
    const template = snapshot.metrics.spindleSpeeds[0];
    const conditionSnapshot = structuredClone(snapshot);
    conditionSnapshot.conditions = [
      { conditionType: "SYSTEM", level: "NORMAL", observation: template.observation, provenance: template.provenance },
      { conditionType: "COMMUNICATIONS", level: "NORMAL", observation: template.observation, provenance: template.provenance },
      { conditionType: "LOGIC_PROGRAM", level: "NORMAL", observation: template.observation, provenance: template.provenance },
      { conditionType: "HYDRAULIC", level: "WARNING", message: "압력 낮음", observation: template.observation, provenance: template.provenance },
    ];

    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot: conditionSnapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getByText("정상 3")).toBeTruthy();
    expect(screen.getByText("주의 1")).toBeTruthy();
    expect(screen.getByText("HYDRAULIC")).toBeTruthy();
    const normalDisclosure = screen.getByText("정상 상태 신호 모두 보기 (3개)").closest("details");
    expect(normalDisclosure).toBeTruthy();
    expect(within(normalDisclosure!).getByText("SYSTEM")).toBeTruthy();
    expect(within(normalDisclosure!).queryByText("HYDRAULIC")).toBeNull();
  });

  it("shows unavailable for optional metrics without inventing zero", () => {
    const partialSnapshot = structuredClone(snapshot);
    delete partialSnapshot.metrics.toolNumber;
    if (partialSnapshot.metrics.program) {
      partialSnapshot.metrics.program.availability = "UNAVAILABLE";
      delete partialSnapshot.metrics.program.value;
    }

    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LIVE", snapshot: partialSnapshot, freshness: "FRESH" }}
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getAllByText("확인할 수 없음")).toHaveLength(3);
    expect(screen.queryByText("0")).toBeNull();
  });

  it("distinguishes loading, not-found, and retryable unavailable states", () => {
    const retryNow = vi.fn();
    const { rerender } = render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "LOADING" }}
        retryNow={retryNow}
      />,
    );
    expect(
      screen.getByRole("heading", { name: "Mazak01 정보를 불러오는 중입니다" }),
    ).toBeTruthy();

    rerender(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "UNAVAILABLE", failure: "NOT_FOUND" }}
        retryNow={retryNow}
      />,
    );
    expect(screen.getByRole("heading", { name: "설비를 찾을 수 없습니다" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "다시 시도" })).toBeNull();

    rerender(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "UNAVAILABLE", failure: "NETWORK" }}
        retryNow={retryNow}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(retryNow).toHaveBeenCalledOnce();
  });
});
