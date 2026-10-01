import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import alarmFixture from "../../../../../../tests/fixtures/alarm/v1/mazak01-alarm-timeline.json";
import paretoFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import type { Alarm } from "../../alarm/domain/alarm";
import type { ShiftOverview } from "../domain/shiftOverview";
import { ShiftOverviewPanel } from "./ShiftOverviewPanel";

afterEach(cleanup);

describe("ShiftOverviewPanel Alarm marker", () => {
  it("uses the same Alarm identity and a textual marker cue", () => {
    const onSeek = vi.fn();
    const report: ShiftOverview = {
      machineId: "Mazak01", replaySessionId: alarmFixture.replaySessionId,
      throughReplaySequence: 100, observedFrom: "2016-10-05T09:00:00Z",
      observedTo: "2016-10-05T09:10:00Z", downtimeSeconds: 0,
      totalMachiningCount: 0, completedMachiningCount: 0,
      intervalProcessingRunId: `sha256:${"a".repeat(64)}`,
      utilizationProcessingRunId: `sha256:${"b".repeat(64)}`,
      intervals: [
        { state: "READY", startedAt: "2016-10-05T09:00:00Z", endedAt: "2016-10-05T09:05:00Z" },
        { state: "INTERRUPTED", startedAt: "2016-10-05T09:05:00Z" },
      ],
      markers: [], pareto: paretoFixture as never,
    };
    render(<ShiftOverviewPanel report={report} alarms={alarmFixture.alarms as Alarm[]}
      onSeek={onSeek} />);

    const marker = screen.getByRole("button", { name: /주의 알람.*345/ });
    expect(marker.getAttribute("data-alarm-id")).toBe(alarmFixture.alarms[0].alarmId);
    expect(marker.querySelector("svg")).toBeTruthy();
    expect(screen.getByRole("region", { name: "확인할 알람" })).toBeTruthy();
    const interrupted = screen.getByRole("button", { name: /가공 중단.*09:05:00/ });
    expect(interrupted.querySelector("svg")).toBeTruthy();
    expect(within(interrupted).getByText("가공 중단")).toBeTruthy();
    fireEvent.click(marker);
    expect(onSeek).toHaveBeenCalledWith(alarmFixture.alarms[0].openedAt);
  });
});
