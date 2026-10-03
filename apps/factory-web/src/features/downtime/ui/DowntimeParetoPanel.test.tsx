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

    const first = screen.getByRole("button", { name: /1위.*멈춤/ });
    expect(within(first).getByText("비상정지 기록")).toBeTruthy();
    expect(within(first).getByText("수동 모드로 바뀜")).toBeTruthy();
    expect(within(first).getByText("경고 406 · DOOR OPEN")).toBeTruthy();
    expect(screen.getByText("함께 기록된 사실 없음")).toBeTruthy();
    expect(screen.getByText("같은 시간에 함께 기록된 사실이에요. 멈춘 원인이라고 단정하지 않아요.")).toBeTruthy();
    // The Pareto ranks every non-operating interval, so it must not call them stops or causes.
    const panel = screen.getByRole("region", { name: "주요 비가동 구간" });
    expect(within(panel).getByText("관측된 비가동 시간")).toBeTruthy();
    expect(within(panel).getByText("멈춤 1분 40초 · 기록 없음 1분")).toBeTruthy();
    const unknown = screen.getByRole("button", { name: /2위.*기록 없음/ });
    expect(unknown.querySelector("svg")).toBeTruthy();
    expect(within(unknown).getByText("기록 없음")).toBeTruthy();
  });

  it("selects the beginning of a ranked downtime interval", () => {
    const onSelect = vi.fn();
    render(
      <DowntimeParetoPanel
        report={paretoFixture as DowntimeParetoReport}
        onSelect={onSelect}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /1위.*멈춤/ }));

    expect(onSelect).toHaveBeenCalledWith("2016-10-05T09:00:00Z");
  });

  it("labels an observed mode change with no source value as unknown", () => {
    const report = structuredClone(paretoFixture) as DowntimeParetoReport;
    const modeChange = report.entries[0]?.evidence.find((evidence) =>
      evidence.kind === "MODE_CHANGE");
    if (modeChange) modeChange.value = "UNKNOWN";

    render(<DowntimeParetoPanel report={report} onSelect={() => undefined} />);

    expect(screen.getByText("운전 모드 기록 없음")).toBeTruthy();
  });
});
