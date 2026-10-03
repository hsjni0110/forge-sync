import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ComponentType } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { TwinSnapshot } from "../../twin/domain/twin";
import type { ReplayControlClient } from "../../replay/application/ports";
import type { ReplaySessionState } from "../../replay/domain/replay";
import type { DowntimeParetoClient } from "../../downtime/application/ports";
import paretoFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-downtime-pareto.json";
import type { DowntimeParetoReport } from "../../downtime/domain/downtimePareto";
import type { OperationalEffectivenessClient } from "../../effectiveness/application/ports";
import type { OperationalEffectivenessReport } from "../../effectiveness/domain/operationalEffectiveness";
import { ShiftOverviewError } from "../../shift-overview/application/ports";
import { DashboardRoute } from "./DashboardRoute";
import type { AlarmClient } from "../../alarm/application/ports";
import type { AlarmTimeline } from "../../alarm/domain/alarm";
import alarmFixture from "../../../../../../tests/fixtures/alarm/v1/mazak01-alarm-timeline.json";

const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;

afterEach(cleanup);

function sessionFactoryFor(state: TwinLiveState): TwinSessionFactory {
  return () => ({
    start: () => undefined,
    dispose: () => undefined,
    retryNow: () => undefined,
    currentState: () => state,
    subscribe: (listener) => {
      listener(state);
      return () => undefined;
    },
  });
}

function renderDashboard(
  state: TwinLiveState,
  replayControlClient?: ReplayControlClient,
  downtimeParetoClient?: DowntimeParetoClient,
  operationalEffectivenessClient?: OperationalEffectivenessClient,
) {
  render(
    <MemoryRouter>
      <DashboardRoute
        twinSessionFactory={sessionFactoryFor(state)}
        replayControlClient={replayControlClient}
        downtimeParetoClient={downtimeParetoClient}
        operationalEffectivenessClient={operationalEffectivenessClient}
      />
    </MemoryRouter>,
  );
}

describe("DashboardRoute", () => {
  it("introduces the dashboard as the whole observed shift overview", () => {
    renderDashboard({ connectionStatus: "LIVE", snapshot, freshness: "FRESH" });

    expect(screen.getByRole("heading", { name: "교대조 개요" })).toBeTruthy();
    expect(screen.getByText(/하루 기록에서 작업한 시간과 멈춘 때를 확인해요/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "시점 상세 보기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });

  it("shows version-aligned shift KPIs and keeps interrupted intervals distinct", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0", replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01", sourceSetId: "nist-mazak01-20161005", status: "PAUSED",
      speedMultiplier: 100, revision: 7,
      sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused), start: vi.fn(), pause: vi.fn(), resume: vi.fn(),
      changeSpeed: vi.fn(), seek: vi.fn(),
    };
    const shiftOverviewClient = {
      load: vi.fn().mockResolvedValue({
        machineId: "Mazak01", replaySessionId: snapshot.replayCursor.replaySessionId,
        throughReplaySequence: snapshot.replayCursor.replaySequence,
        observedFrom: "2016-10-05T05:27:55.740Z", observedTo: "2016-10-05T19:15:07.025Z",
        availabilityPercent: 30, cuttingPercent: 40, stoppedSeconds: 100, unknownSeconds: 60,
        totalMachiningCount: 122, completedMachiningCount: 94,
        intervalProcessingRunId: `sha256:${"a".repeat(64)}`,
        utilizationProcessingRunId: `sha256:${"b".repeat(64)}`,
        intervals: [
          { state: "ACTIVE", startedAt: "2016-10-05T09:00:00Z", endedAt: "2016-10-05T09:01:00Z" },
          { state: "INTERRUPTED", startedAt: "2016-10-05T09:01:00Z", endedAt: "2016-10-05T09:02:00Z" },
        ],
        markers: [{ kind: "TOOL_CHANGE", sourceObservedAt: "2016-10-05T09:01:30Z",
          seekTo: "2016-10-05T09:01:30Z", label: "공구 교체 2번에서 7번" }],
        pareto: { ...structuredClone(paretoFixture),
          replaySessionId: snapshot.replayCursor.replaySessionId,
          throughReplaySequence: snapshot.replayCursor.replaySequence,
          totalDowntimeSeconds: 160 },
      }),
    };
    const alarmClient: AlarmClient = {
      find: vi.fn().mockResolvedValue(alarmFixture as AlarmTimeline), acknowledge: vi.fn(),
    };
    const ShiftAwareDashboard = DashboardRoute as unknown as ComponentType<Record<string, unknown>>;

    const { container } = render(<MemoryRouter><ShiftAwareDashboard twinSessionFactory={sessionFactoryFor({
      connectionStatus: "LIVE", snapshot, freshness: "FRESH",
    })} replayControlClient={replayClient} shiftOverviewClient={shiftOverviewClient}
      alarmClient={alarmClient} /></MemoryRouter>);

    const readiness = await screen.findByRole("region", { name: "분석 가능 상태" });
    const daySummary = await screen.findByRole("list", { name: "하루 요약" });
    expect(screen.getByRole("heading", { name: /^기계가 실제로 작업한 시간은 하루의 30%예요\./ }))
      .toBeTruthy();
    expect(within(daySummary).getByText("30%")).toBeTruthy();
    const otherKpis = screen.getByRole("region", { name: "그 밖의 지표" });
    expect(within(otherKpis).getByText("40%")).toBeTruthy();
    expect(within(otherKpis).getByText("전체 122건")).toBeTruthy();
    expect(within(otherKpis).getByText("완료 94건")).toBeTruthy();
    expect(within(otherKpis).getByText("선택 시점 데이터")).toBeTruthy();
    expect(screen.getByRole("region", { name: "주요 비가동 구간" })).toBeTruthy();
    expect(screen.queryByText("주축 속도")).toBeNull();
    const interrupted = screen.getByRole("button", { name: /작업 중단.*09:01:00.*09:02:00/ });
    expect(interrupted.querySelector("svg")).toBeTruthy();
    expect(within(interrupted).getByText("작업 중단")).toBeTruthy();
    expect(screen.queryByRole("region", { name: "확인할 알람" })).toBeNull();
    fireEvent.click(await screen.findByRole("button", { name: "알람 1건 함께 보기" }));
    const alarmRegion = screen.getByRole("region", { name: "확인할 알람" });
    expect(alarmRegion.querySelector("svg")).toBeTruthy();
    expect(within(alarmRegion).getByText("주의 알람")).toBeTruthy();
    expect(otherKpis.querySelectorAll(".metric-card")).toHaveLength(0);
    expect(container.querySelectorAll(".shift-kpi-band")).toHaveLength(1);
    expect(screen.getAllByRole("link", { name: "시점 상세 보기" })).toHaveLength(1);

    // Above the conclusion only the scope remains; the shell's top bar already states the link,
    // freshness and replay, and the title and readiness stay for screen readers.
    expect(screen.queryByRole("region", { name: "트윈 연결 상태" })).toBeNull();
    expect(screen.getByRole("heading", { level: 1, name: "교대조 개요" }).closest("header")?.className)
      .toContain("cds--visually-hidden");
    expect(readiness.className).toContain("cds--visually-hidden");
    const details = screen.getByText("자세히 보기").closest("details")!;
    expect(within(details).getByRole("region", { name: "주요 비가동 구간", hidden: true })).toBeTruthy();

    const scanOrder = [
      readiness,
      screen.getByRole("heading", { name: /^기계가 실제로 작업한 시간은 하루의 30%예요\./ }),
      screen.getByRole("region", { name: "설비 상태 구간" }),
      alarmRegion,
      daySummary,
      otherKpis,
      screen.getByRole("region", { name: "주요 비가동 구간" }),
      screen.getByRole("link", { name: "시점 상세 보기" }),
    ];
    for (let index = 0; index < scanOrder.length - 1; index += 1) {
      expect(
        scanOrder[index]!.compareDocumentPosition(scanOrder[index + 1]!)
          & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
    fireEvent.click(screen.getByRole("button", { name: "공구 교체 1건 함께 보기" }));
    fireEvent.click(screen.getByRole("button", { name: "공구 교체 2번에서 7번" }));
    await waitFor(() => expect(replayClient.seek).toHaveBeenCalledWith(
      paused.replaySessionId, paused.revision, "2016-10-05T09:01:30Z", paused.speedMultiplier,
    ));
    expect(screen.getByRole("button", { name: /1위.*멈춤/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: /주의 알람.*345/ })
      .getAttribute("data-alarm-id")).toBe(alarmFixture.alarms[0].alarmId);
    expect(screen.queryByText(/품질 100%/)).toBeNull();
  });

  it("announces shift loading and a version mismatch instead of keeping stale results", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0", replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01", sourceSetId: "nist-mazak01-20161005", status: "PAUSED",
      speedMultiplier: 100, revision: 7,
      sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused), start: vi.fn(), pause: vi.fn(), resume: vi.fn(),
      changeSpeed: vi.fn(), seek: vi.fn(),
    };
    let rejectLoad: (failure: unknown) => void = () => undefined;
    const shiftOverviewClient = { load: vi.fn().mockReturnValue(new Promise((_resolve, reject) => {
      rejectLoad = reject;
    })) };
    const ShiftAwareDashboard = DashboardRoute as unknown as ComponentType<Record<string, unknown>>;
    render(<MemoryRouter><ShiftAwareDashboard twinSessionFactory={sessionFactoryFor({
      connectionStatus: "LIVE", snapshot, freshness: "FRESH",
    })} replayControlClient={replayClient} shiftOverviewClient={shiftOverviewClient} /></MemoryRouter>);

    expect(await screen.findByText("교대조 분석을 불러오고 있어요.")).toBeTruthy();
    rejectLoad(new ShiftOverviewError("VERSION_MISMATCH"));
    expect((await screen.findByRole("alert")).textContent).toMatch("분석 버전이 맞지 않아요");
    expect(screen.queryByRole("region", { name: "교대조 핵심 지표" })).toBeNull();
  });

  it("waits for the twin to reach the paused replay cursor before starting shift analysis", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0", replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01", sourceSetId: "nist-mazak01-20161005", status: "PAUSED",
      speedMultiplier: 100, revision: 7,
      sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
      publicationCursor: {
        replaySequence: snapshot.replayCursor.replaySequence + 1,
        sourceObservedAt: "2016-10-05T19:15:07.025Z",
        replayPublishedAt: "2026-09-12T00:00:00Z",
      },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused), start: vi.fn(), pause: vi.fn(), resume: vi.fn(),
      changeSpeed: vi.fn(), seek: vi.fn(),
    };
    const shiftOverviewClient = { load: vi.fn() };
    const ShiftAwareDashboard = DashboardRoute as unknown as ComponentType<Record<string, unknown>>;

    render(<MemoryRouter><ShiftAwareDashboard twinSessionFactory={sessionFactoryFor({
      connectionStatus: "LIVE", snapshot, freshness: "FRESH",
    })} replayControlClient={replayClient} shiftOverviewClient={shiftOverviewClient} /></MemoryRouter>);

    expect(await screen.findByText("재생 데이터가 화면에 반영되기를 기다리고 있어요.")).toBeTruthy();
    expect(shiftOverviewClient.load).not.toHaveBeenCalled();
    expect(screen.queryByText("교대조 분석을 불러오고 있어요.")).toBeNull();
    expect(screen.queryByRole("region", { name: "교대조 핵심 지표" })).toBeNull();
  });

  it("shows a loading placeholder before the first snapshot", () => {
    renderDashboard({ connectionStatus: "LOADING" });

    expect(screen.getByRole("heading", { name: "교대조 개요" })).toBeTruthy();
    expect(screen.getByText(/설비 상태를 불러오는 중입니다/)).toBeTruthy();
    expect(screen.getByRole("link", { name: "시점 상세 보기" }).getAttribute("href")).toBe(
      "/factory",
    );
  });

  it("summarizes the live twin state as KPI tiles", () => {
    renderDashboard({ connectionStatus: "LIVE", snapshot, freshness: "FRESH" });

    const execution = screen.getByText("가동 상태").closest(".metric-card") as HTMLElement;
    expect(within(execution).getByText("가동 중")).toBeTruthy();
    const spindle = screen.getByText("주축 속도").closest(".metric-card") as HTMLElement;
    expect(within(spindle).getByText("49 rpm")).toBeTruthy();
    expect(screen.getByText(/트윈 데이터 버전/)).toBeTruthy();
  });

  it("warns when the twin data is stale", () => {
    renderDashboard({ connectionStatus: "RECONNECTING", snapshot, freshness: "STALE" });

    expect(screen.getByRole("alert").textContent).toMatch(/실시간 상태로 판단하지 마세요/);
  });

  it("presents a completed replay instead of a realtime stale warning", async () => {
    const completed: ReplaySessionState = {
      schemaVersion: "1.0.0",
      replaySessionId: "10000000-0000-4000-8000-000000000001",
      machineId: "Mazak01",
      sourceSetId: "nist-mazak01-20161005",
      status: "COMPLETED",
      speedMultiplier: 10,
      revision: 2,
      sourceRange: {
        startsAt: "2016-10-05T05:27:55.740Z",
        endsAt: "2016-10-05T19:15:07.025Z",
      },
    };
    const client: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(completed),
      start: vi.fn(), pause: vi.fn(), resume: vi.fn(), changeSpeed: vi.fn(), seek: vi.fn(),
    };
    renderDashboard(
      { connectionStatus: "LIVE", snapshot, freshness: "STALE" },
      client,
    );

    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/재생이 완료/));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getAllByText("마지막 재생 데이터").length).toBeGreaterThan(0);
  });

  it("seeks the replay to the selected downtime interval", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0",
      replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01",
      sourceSetId: "nist-mazak01-20161005",
      status: "PAUSED",
      speedMultiplier: 10,
      revision: 7,
      sourceRange: {
        startsAt: "2016-10-05T05:27:55.740Z",
        endsAt: "2016-10-05T19:15:07.025Z",
      },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused),
      start: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      changeSpeed: vi.fn(),
      seek: vi.fn().mockResolvedValue(paused),
    };
    const paretoClient: DowntimeParetoClient = {
      analyze: vi.fn().mockResolvedValue({
        ...paretoFixture,
        replaySessionId: snapshot.replayCursor.replaySessionId,
        throughReplaySequence: snapshot.replayCursor.replaySequence,
      } as DowntimeParetoReport),
    };
    renderDashboard(
      { connectionStatus: "LIVE", snapshot, freshness: "FRESH" },
      replayClient,
      paretoClient,
    );

    fireEvent.click(await screen.findByRole("button", { name: /1위.*멈춤/ }));

    await waitFor(() =>
      expect(replayClient.seek).toHaveBeenCalledWith(
        paused.replaySessionId,
        7,
        "2016-10-05T09:00:00Z",
        10,
      ),
    );
  });

  it("shows evidence states at a stable replay cursor without inventing composite OEE", async () => {
    const paused: ReplaySessionState = {
      schemaVersion: "1.0.0", replaySessionId: snapshot.replayCursor.replaySessionId,
      machineId: "Mazak01", sourceSetId: "nist-mazak01-20161005", status: "PAUSED",
      speedMultiplier: 10, revision: 7,
      sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
    };
    const replayClient: ReplayControlClient = {
      load: vi.fn().mockResolvedValue(paused), start: vi.fn(), pause: vi.fn(), resume: vi.fn(),
      changeSpeed: vi.fn(), seek: vi.fn(),
    };
    const effectivenessClient: OperationalEffectivenessClient = {
      analyze: vi.fn().mockResolvedValue({
        availability: { status: "AVAILABLE", percent: 70, sourceProvenance: "OBSERVED",
          valueProvenance: "DERIVED", formula: "ACTIVE_DURATION / OBSERVED_RANGE" },
        performance: { status: "UNAVAILABLE", provenance: "UNAVAILABLE", sampleCount: 2,
          contributingFeatureSetIds: [], reason: "MINIMUM_SAMPLE_COUNT_NOT_MET" },
        throughput: { status: "UNAVAILABLE", usedTransitionCount: 0, resetCount: 0,
          unavailableObservationCount: 8, reason: "NO_USABLE_TRANSITIONS" },
        quality: { status: "UNAVAILABLE", provenance: "UNAVAILABLE",
          reason: "QUALITY_SOURCE_NOT_AVAILABLE" },
        compositeOee: { status: "UNAVAILABLE", provenance: "UNAVAILABLE",
          reason: "QUALITY_COMPONENT_UNAVAILABLE" },
      } as unknown as OperationalEffectivenessReport),
    };

    renderDashboard({ connectionStatus: "LIVE", snapshot, freshness: "FRESH" },
      replayClient, undefined, effectivenessClient);

    expect(await screen.findByText("종합 OEE 제공 불가")).toBeTruthy();
    expect(screen.getByText("표본 부족 (2/5)")).toBeTruthy();
    expect(effectivenessClient.analyze).toHaveBeenCalledWith("Mazak01",
      snapshot.replayCursor.replaySessionId, snapshot.replayCursor.replaySequence, expect.anything());
  });
});
