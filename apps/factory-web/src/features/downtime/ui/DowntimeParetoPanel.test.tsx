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
});
