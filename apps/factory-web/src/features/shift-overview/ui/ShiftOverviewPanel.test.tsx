import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
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
      observedTo: "2016-10-05T09:10:00Z", stoppedSeconds: 0, unknownSeconds: 0,
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
    fireEvent.click(screen.getByRole("button", { name: "알람 1건 함께 보기" }));

    const marker = screen.getByRole("button", { name: /주의 알람.*345/ });
    expect(marker.getAttribute("data-alarm-id")).toBe(alarmFixture.alarms[0].alarmId);
    expect(marker.querySelector("svg")).toBeTruthy();
    expect(screen.getByRole("region", { name: "확인할 알람" })).toBeTruthy();
    const interrupted = screen.getByRole("button", { name: /작업 중단.*09:05:00/ });
    expect(interrupted.querySelector("svg")).toBeTruthy();
    expect(within(interrupted).getByText("작업 중단")).toBeTruthy();
    fireEvent.click(marker);
    expect(onSeek).toHaveBeenCalledWith(alarmFixture.alarms[0].openedAt);
  });
});

type ParetoEntry = ShiftOverview["pareto"]["entries"][number];

function paretoEntry(rank: number, state: ParetoEntry["state"], startedAt: string, endedAt: string,
  evidence: ParetoEntry["evidence"] = []): ParetoEntry {
  return {
    rank, state, startedAt, endedAt,
    durationSeconds: (Date.parse(endedAt) - Date.parse(startedAt)) / 1000,
    ratioPercent: 0, cumulativeRatioPercent: 0, evidence,
    reasonClassification: evidence.length > 0 ? "CONCURRENT_EVIDENCE" : "UNCONFIRMED_REASON",
  };
}

function dayReport(overrides: Partial<ShiftOverview> = {}, entries: ParetoEntry[] = [
  paretoEntry(1, "UNKNOWN", "2016-10-05T06:00:00Z", "2016-10-05T08:00:00Z"),
  paretoEntry(2, "STOPPED", "2016-10-05T17:53:38Z", "2016-10-05T18:23:52Z", [{
    kind: "MODE_CHANGE", signal: "CONTROLLER_MODE", value: "MANUAL",
    sourceObservedAt: "2016-10-05T17:53:38Z", replaySequence: 9, sourceEventKey: "mode-9",
  }]),
  paretoEntry(3, "STOPPED", "2016-10-05T08:43:49Z", "2016-10-05T09:01:33Z"),
]): ShiftOverview {
  return {
    machineId: "Mazak01", replaySessionId: alarmFixture.replaySessionId,
    throughReplaySequence: 100, observedFrom: "2016-10-05T05:27:55Z",
    observedTo: "2016-10-05T19:15:07Z", availabilityPercent: 23.47, cuttingPercent: 31,
    activeSeconds: 11_640,
    stoppedSeconds: 10_903, unknownSeconds: 12_655,
    totalMachiningCount: 122, completedMachiningCount: 102,
    intervalProcessingRunId: `sha256:${"a".repeat(64)}`,
    utilizationProcessingRunId: `sha256:${"b".repeat(64)}`,
    intervals: [
      { state: "ACTIVE", startedAt: "2016-10-05T05:27:55Z", endedAt: "2016-10-05T17:53:38Z" },
      { state: "STOPPED", startedAt: "2016-10-05T17:53:38Z", endedAt: "2016-10-05T18:23:52Z" },
      { state: "READY", startedAt: "2016-10-05T18:23:52Z", endedAt: "2016-10-05T19:15:07Z" },
    ],
    markers: [],
    pareto: { ...(paretoFixture as never as ShiftOverview["pareto"]), entries },
    ...overrides,
  };
}

describe("ShiftOverviewPanel day summary", () => {
  it("states one conclusion and three facts in screen display terms", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()} onSeek={vi.fn()} /></MemoryRouter>);

    expect(within(screen.getByRole("navigation", { name: "보는 범위" }))
      .getByText("Mazak01 · 2016년 10월 5일")).toBeTruthy();
    expect(screen.getByText("하루 전체 · 05:27–19:15 · 13시간 47분")).toBeTruthy();
    // The longer UNKNOWN interval has no record of a stop, so it is not the longest stop.
    expect(screen.getByRole("heading", { level: 2 }).textContent).toBe(
      "기계가 실제로 작업한 시간은 하루의 23.5%예요. 가장 오래 멈춘 때는 17:53부터 18:23까지예요.",
    );
    const facts = screen.getByRole("list", { name: "하루 요약" });
    expect(within(facts).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "작업23.5%3시간 14분",
      "멈춤3시간 1분멈춤과 작업 중단을 합친 시간",
      "기록 없음3시간 30분데이터가 끊긴 시간이라 멈춤에 넣지 않았어요",
    ]);
    const legend = screen.getByRole("list", { name: "상태 범례" });
    expect(within(legend).getAllByRole("listitem").map((item) => item.textContent))
      .toEqual(["작업", "멈춤", "기록 없음", "빈 곳은 대기"]);
  });

  it("says what it cannot conclude instead of inventing a number or a stop", () => {
    render(<ShiftOverviewPanel report={dayReport({ availabilityPercent: undefined }, [
      paretoEntry(1, "UNKNOWN", "2016-10-05T06:00:00Z", "2016-10-05T08:00:00Z"),
    ])} onSeek={vi.fn()} />);

    expect(screen.getByRole("heading", { level: 2 }).textContent)
      .toBe("작업한 시간을 계산할 근거가 부족해요. 멈춤으로 기록된 때는 없어요.");
    expect(screen.queryByRole("button", { name: /가장 오래 멈춘 때 보기/ })).toBeNull();
  });
});

const threeStops = [
  paretoEntry(1, "UNKNOWN", "2016-10-05T06:00:00Z", "2016-10-05T08:00:00Z"),
  paretoEntry(2, "STOPPED", "2016-10-05T17:53:38Z", "2016-10-05T18:23:52Z"),
  paretoEntry(3, "STOPPED", "2016-10-05T08:43:49Z", "2016-10-05T09:01:33Z"),
  paretoEntry(4, "INTERRUPTED", "2016-10-05T13:27:20Z", "2016-10-05T13:43:51Z"),
  paretoEntry(5, "STOPPED", "2016-10-05T11:00:00Z", "2016-10-05T11:01:00Z"),
];

describe("ShiftOverviewPanel stop focus", () => {
  it("zooms the same bar into the longest stop and states what was recorded with it", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()} onSeek={vi.fn()} /></MemoryRouter>);

    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));

    expect(within(screen.getByRole("navigation", { name: "보는 범위" })).getByText("17:53–18:23")
      .getAttribute("aria-current")).toBe("page");
    expect(screen.getByText("17:53–18:23 구간")).toBeTruthy();
    expect(screen.getByRole("heading", { level: 2 }).textContent).toBe(
      "30분 14초 동안 멈춰 있었어요. 같은 시간에 수동 모드로 바뀐 기록이 있어요.",
    );
    expect(screen.getByText(
      "함께 기록된 사실만 보여 드려요. 멈춘 이유를 확인하려면 현장 작업 기록과 함께 보세요.",
    )).toBeTruthy();
    const facts = within(screen.getByRole("list", { name: "멈춘 때 요약" })).getAllByRole("listitem");
    expect(facts.map((item) => item.textContent)).toEqual([
      "길이30분 14초", "상태멈춤", "운전 모드수동 모드로 바뀜같은 시간에 기록됨", "경고·비상정지없음",
    ]);
    // Half the stop's length on each side keeps its neighbours in view.
    const stopped = screen.getByRole("button", { name: /^멈춤 17:53:38/ });
    expect(stopped.style.left).toBe("25%");
    expect(stopped.style.width).toBe("50%");
    expect(screen.getByText("막대를 누르거나 끌면 그 순간의 기계를 볼 수 있어요.")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "하루 전체" }));
    expect(screen.getByRole("heading", { level: 2 }).textContent)
      .toMatch(/^기계가 실제로 작업한 시간은 하루의 23\.5%예요\./);
  });

  it("says nothing else was recorded rather than leaving the stop unexplained", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport({}, [
      paretoEntry(1, "INTERRUPTED", "2016-10-05T08:43:49Z", "2016-10-05T09:01:33Z"),
    ])} onSeek={vi.fn()} /></MemoryRouter>);

    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));

    expect(screen.getByRole("heading", { level: 2 }).textContent)
      .toBe("17분 44초 동안 작업이 중단돼 있었어요.");
    expect(within(screen.getByRole("list", { name: "멈춘 때 요약" })).getAllByRole("listitem")
      .map((item) => item.textContent)).toEqual([
      "길이17분 44초", "상태작업 중단", "운전 모드바뀐 기록 없음", "경고·비상정지없음",
    ]);
  });

  it("steps through the three longest stops in a loop", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport({}, threeStops)} onSeek={vi.fn()} />
    </MemoryRouter>);
    const scope = () => screen.getByRole("navigation", { name: "보는 범위" });

    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));
    fireEvent.click(screen.getByRole("button", { name: "다음 →" }));
    expect(within(scope()).getByText("08:43–09:01")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "← 이전" }));
    fireEvent.click(screen.getByRole("button", { name: "← 이전" }));
    expect(within(scope()).getByText("13:27–13:43")).toBeTruthy();
  });
});

describe("ShiftOverviewPanel alarm layer", () => {
  it("keeps alarms off the bar until asked and only places those inside the visible range", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()}
      alarms={alarmFixture.alarms as Alarm[]} onSeek={vi.fn()} /></MemoryRouter>);

    const layer = screen.getByRole("button", { name: "알람 1건 함께 보기" });
    expect(layer.getAttribute("aria-pressed")).toBe("false");
    expect(screen.queryByRole("button", { name: /주의 알람.*345/ })).toBeNull();
    expect(screen.queryByRole("region", { name: "확인할 알람" })).toBeNull();

    fireEvent.click(layer);
    expect(layer.getAttribute("aria-pressed")).toBe("true");
    expect(layer.textContent).toBe("알람 숨기기");
    expect(screen.getByRole("button", { name: /주의 알람.*345/ })).toBeTruthy();

    // The 09:01 alarm lies outside the 17:53 stop, so it must not be pinned to the bar's edge.
    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));
    expect(screen.queryByRole("button", { name: /주의 알람.*345/ })).toBeNull();
  });
});

describe("ShiftOverviewPanel reading order", () => {
  it("reads conclusion, bar, facts, then one action, and folds the rest away", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()} onSeek={vi.fn()}>
      <p>비가동 순위 자리</p>
    </ShiftOverviewPanel></MemoryRouter>);

    const scope = screen.getByRole("navigation", { name: "보는 범위" });
    expect(within(scope).getByText("하루 전체").getAttribute("aria-current")).toBe("page");
    expect(screen.getByText("06:00")).toBeTruthy();
    expect(screen.getByText("18:00")).toBeTruthy();

    const order = [
      screen.getByRole("heading", { level: 2 }),
      screen.getByRole("region", { name: "설비 상태 구간" }),
      screen.getByRole("list", { name: "하루 요약" }),
      screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }),
    ];
    for (let index = 0; index < order.length - 1; index += 1) {
      expect(order[index]!.compareDocumentPosition(order[index + 1]!)
        & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }

    const details = screen.getByText("자세히 보기").closest("details");
    expect(details?.open).toBe(false);
    expect(within(details!).getByRole("region", { name: "그 밖의 지표", hidden: true })).toBeTruthy();
    expect(within(details!).getByText("비가동 순위 자리")).toBeTruthy();
  });

  it("outlines the longest stop on the day bar and opens it from there", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()} onSeek={vi.fn()} /></MemoryRouter>);

    fireEvent.click(screen.getByRole("button", { name: "17:53–18:23 자세히 보기" }));

    expect(screen.getByText("17:53–18:23 구간")).toBeTruthy();
    expect(screen.queryByText("자세히 보기")).toBeNull();
  });
});

describe("ShiftOverviewPanel tool change layer", () => {
  it("keeps tool changes off the bar until asked", () => {
    const onSeek = vi.fn();
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport({ markers: [{
      kind: "TOOL_CHANGE", sourceObservedAt: "2016-10-05T09:01:30Z",
      seekTo: "2016-10-05T09:01:30Z", label: "공구 교체 2번에서 7번",
    }] })} onSeek={onSeek} /></MemoryRouter>);

    expect(screen.queryByRole("button", { name: "공구 교체 2번에서 7번" })).toBeNull();
    const layer = screen.getByRole("button", { name: "공구 교체 1건 함께 보기" });
    fireEvent.click(layer);

    expect(layer.getAttribute("aria-pressed")).toBe("true");
    expect(layer.textContent).toBe("공구 교체 숨기기");
    fireEvent.click(screen.getByRole("button", { name: "공구 교체 2번에서 7번" }));
    expect(onSeek).toHaveBeenCalledWith("2016-10-05T09:01:30Z");
  });
});

describe("ShiftOverviewPanel alarm after the last state record", () => {
  it("lists the alarm with its identity instead of pinning it to the bar's edge", () => {
    const late = { ...alarmFixture.alarms[0], openedAt: "2016-10-05T19:20:00Z" } as Alarm;
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport()} alarms={[late]}
      onSeek={vi.fn()} /></MemoryRouter>);

    fireEvent.click(screen.getByRole("button", { name: "알람 1건 함께 보기" }));

    expect(within(screen.getByRole("region", { name: "설비 상태 구간" }))
      .queryByRole("button", { name: /주의 알람/ })).toBeNull();
    const listed = within(screen.getByRole("region", { name: "확인할 알람" }))
      .getByRole("button", { name: /코드 345 · 19:20:00 UTC/ });
    expect(listed.getAttribute("data-alarm-id")).toBe(late.alarmId);
  });
});


describe("ShiftOverviewPanel other long stops", () => {
  it("offers the next two longest stops by their start time and opens either one", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport({}, threeStops)} onSeek={vi.fn()} />
    </MemoryRouter>);

    const others = screen.getByRole("group", { name: "다른 긴 멈춤" });
    expect(within(others).getAllByRole("button").map((button) => button.textContent))
      .toEqual(["08:43", "13:27"]);

    fireEvent.click(within(others).getByRole("button", { name: "13:27" }));

    expect(within(screen.getByRole("navigation", { name: "보는 범위" })).getByText("13:27–13:43"))
      .toBeTruthy();
  });
});

describe("ShiftOverviewPanel moment picking", () => {
  it("seeks to the moment picked on the zoomed bar and opens the factory view there", () => {
    const onSeek = vi.fn();
    render(<MemoryRouter><Routes>
      <Route path="/" element={<ShiftOverviewPanel report={dayReport()} onSeek={onSeek} />} />
      <Route path="/factory" element={<p>공장 보기 화면</p>} />
    </Routes></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));
    expect(screen.queryByRole("link", { name: "이 시점 재생하기" })).toBeNull();

    const picker = screen.getByRole("slider", { name: "볼 시각 고르기" });
    fireEvent.change(picker, { target: { value: String(Date.parse("2016-10-05T18:00:00Z") / 1000) } });
    expect(screen.getByText("18:00:00")).toBeTruthy();
    expect(onSeek).not.toHaveBeenCalled();
    fireEvent.pointerUp(picker);

    expect(onSeek).toHaveBeenCalledWith("2016-10-05T18:00:00.000Z");
    expect(screen.getByText("공장 보기 화면")).toBeTruthy();
  });

  it("keeps the picker inside the recorded range even though the zoomed view is wider", () => {
    render(<MemoryRouter><ShiftOverviewPanel report={dayReport({}, [
      paretoEntry(1, "STOPPED", "2016-10-05T18:45:00Z", "2016-10-05T19:15:07Z"),
    ])} onSeek={vi.fn()} /></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "가장 오래 멈춘 때 보기" }));

    const picker = screen.getByRole("slider", { name: "볼 시각 고르기" });
    expect(Number(picker.getAttribute("max"))).toBe(Date.parse("2016-10-05T19:15:07Z") / 1000);
  });
});
