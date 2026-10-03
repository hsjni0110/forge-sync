import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import paretoFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import type { DowntimeParetoReport } from "../domain/downtimePareto";
import { DowntimeParetoPanel } from "./DowntimeParetoPanel";

afterEach(cleanup);

describe("DowntimeParetoPanel", () => {
  it("shows every concurrent evidence and keeps an unconfirmed reason explicit", () => {
    render(
      <DowntimeParetoPanel
        report={paretoFixture as DowntimeParetoReport}
        onSelect={() => undefined}
      />,
    );

    const first = screen.getByRole("button", { name: /1위.*정지/ });
    expect(within(first).getByText(/비상정지/)).toBeTruthy();
    expect(within(first).getByText(/운전 모드 변경/)).toBeTruthy();
    expect(within(first).getByText(/상태 경고.*DOOR OPEN/)).toBeTruthy();
    expect(screen.getByText("사유 미확인")).toBeTruthy();
    expect(screen.getByText(/원인으로 확정하지 않습니다/)).toBeTruthy();
    // The Pareto ranks every non-operating interval, so it must not call them stops or causes.
    const panel = screen.getByRole("region", { name: "주요 비가동 구간" });
    expect(within(panel).getByText("관측된 비가동 시간")).toBeTruthy();
    expect(within(panel).getByText("정지·중단 1분 40초 · 확인 불가 1분")).toBeTruthy();
    const unknown = screen.getByRole("button", { name: /2위.*상태 미확인/ });
    expect(unknown.querySelector("svg")).toBeTruthy();
    expect(within(unknown).getByText("상태 미확인")).toBeTruthy();
  });

  it("selects the beginning of a ranked downtime interval", () => {
    const onSelect = vi.fn();
    render(
      <DowntimeParetoPanel
        report={paretoFixture as DowntimeParetoReport}
        onSelect={onSelect}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /1위.*정지/ }));

    expect(onSelect).toHaveBeenCalledWith("2016-10-05T09:00:00Z");
  });

  it("labels an observed mode change with no source value as unknown", () => {
    const report = structuredClone(paretoFixture) as DowntimeParetoReport;
    const modeChange = report.entries[0]?.evidence.find((evidence) =>
      evidence.kind === "MODE_CHANGE");
    if (modeChange) modeChange.value = "UNKNOWN";

    render(<DowntimeParetoPanel report={report} onSelect={() => undefined} />);

    expect(screen.getByText("운전 모드 변경 · 값 확인 불가")).toBeTruthy();
  });
});
