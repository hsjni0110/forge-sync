import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/alarm/v1/mazak01-alarm-timeline.json";
import type { Alarm } from "../domain/alarm";
import { AlarmPanel } from "./AlarmPanel";

afterEach(cleanup);

describe("AlarmPanel", () => {
  it("shows condition-derived identity, text severity cue, and lifecycle status", () => {
    render(<AlarmPanel alarms={fixture.alarms as Alarm[]} onAcknowledge={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "확인할 알람" })).toBeTruthy();
    expect(screen.getByRole("region", { name: "확인할 알람" })).toBeTruthy();
    expect(screen.getByText("주의 알람")).toBeTruthy();
    expect(screen.getByText("주의 알람").closest("strong")?.querySelector("svg")).toBeTruthy();
    expect(screen.getByText(/Condition에서 생성/)).toBeTruthy();
    expect(screen.getByText(/345/)).toBeTruthy();
    expect(screen.getByText(fixture.alarms[0].alarmId)).toBeTruthy();
  });

  it("requires an operator name and acknowledges the same alarm", async () => {
    const acknowledge = vi.fn().mockResolvedValue(undefined);
    render(<AlarmPanel alarms={fixture.alarms as Alarm[]} onAcknowledge={acknowledge} />);
    const button = screen.getByRole("button", { name: "알람 확인" });

    expect(button).toHaveProperty("disabled", true);
    fireEvent.change(screen.getByLabelText("작업자 이름"), { target: { value: "김 작업자" } });
    fireEvent.click(button);

    await waitFor(() => expect(acknowledge).toHaveBeenCalledWith(
      expect.objectContaining({ alarmId: fixture.alarms[0].alarmId }), "김 작업자",
    ));
  });
});
