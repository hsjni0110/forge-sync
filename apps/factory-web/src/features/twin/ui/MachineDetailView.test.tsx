import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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

    expect(screen.getByRole("heading", { name: "Mazak01" })).toBeTruthy();
    for (const section of [
      "기본 정보",
      "현재 상태",
      "측정값",
      "상태 신호",
      "데이터 품질",
      "데이터 출처",
    ]) {
      expect(screen.getByRole("heading", { name: section })).toBeTruthy();
    }
    expect(screen.getByText("49 rpm")).toBeTruthy();
    expect(screen.getByText("13")).toBeTruthy();
    expect(screen.getByText("114")).toBeTruthy();
    expect(screen.getAllByText(/실제 데이터 · NIST/).length).toBeGreaterThan(0);
    expect(screen.getByText(/상세 품질 정보는 아직 제공하지 않습니다/)).toBeTruthy();
  });

  it("keeps values visible while reconnecting and warns when stale", () => {
    render(
      <MachineDetailView
        machineId="Mazak01"
        state={{ connectionStatus: "RECONNECTING", snapshot, freshness: "STALE" }}
        retryNow={vi.fn()}
      />,
    );

    expect(screen.getByText("49 rpm")).toBeTruthy();
    expect(screen.getByText(/마지막으로 받은 값을 표시합니다/)).toBeTruthy();
    expect(screen.getByRole("alert").textContent).toMatch(/실시간 상태로 판단하지 마세요/);
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

    expect(screen.getAllByText("확인할 수 없음")).toHaveLength(2);
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
