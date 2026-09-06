import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import runFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-machining-runs.json";
import featureFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-cycle-features.json";
import assessmentFixture from "../../../../../../tests/fixtures/process-analytics/v1/mazak01-anomaly-assessments.json";
import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../domain/twin";
import type { TwinSessionFactory } from "../application/ports";
import type { ReplayControlClient } from "../../replay/application/ports";
import type { AssessmentDocument } from "../../process-analytics/adapters/processDocuments";
import { FactoryRoute } from "../../factory3d/ui/FactoryRoute";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

// DERIVED_FIXTURE: clones of the reviewed contract fixtures; only terminal evidence is
// removed for the open-watermark case. No changed value is claimed as a source observation.
function showProcess(document: typeof runFixture, mismatch: boolean | "recover" = false,
  configureAssessment?: (assessment: AssessmentDocument["assessments"][number]) => void) {
  const snapshot = structuredClone(twinFixture) as unknown as TwinSnapshot;
  snapshot.replayCursor = {
    ...snapshot.replayCursor,
    replaySessionId: document.replaySessionId,
    replaySequence: document.throughReplaySequence,
    sourceObservedAt: "2016-10-05T09:20:59.181Z",
  };
  const features = structuredClone(featureFixture);
  features.machiningRunProcessingRunId = mismatch ? "sha256:" + "9".repeat(64) : document.processingRunId;
  features.featureSets = document.machiningRuns.filter((run) => run.status === "COMPLETED").map((run) => ({
    ...features.featureSets[0], machiningRunId: run.machiningRunId,
    aggregationWindow: { ...features.featureSets[0].aggregationWindow, startedAt: run.startedAt, endedAt: run.endedAt },
  }));
  features.eligibleRunCount = features.featureSets.length;
  const assessments = structuredClone(assessmentFixture) as unknown as AssessmentDocument;
  assessments.machiningRunProcessingRunId = document.processingRunId;
  assessments.cycleFeatureProcessingRunId = features.featureProcessingRunId;
  assessments.assessments = features.featureSets.map((set) => ({ ...assessments.assessments[0],
    machiningRunId: set.machiningRunId, targetFeatureSetId: set.cycleFeatureSetId,
    baseline: { ...assessments.assessments[0].baseline, targetFeatureSetId: set.cycleFeatureSetId },
    lineage: { ...assessments.assessments[0].lineage, inputCycleFeature: {
      ...assessments.assessments[0].lineage.inputCycleFeature, featureProcessingRunId: features.featureProcessingRunId,
    } },
  }));
  assessments.assessments.forEach((assessment) => configureAssessment?.(assessment));
  let featureRequests = 0;
  const fetch = vi.fn(async (url: string, _options?: RequestInit) => {
    expect(_options?.headers).toBeDefined();
    if (url.includes("cycle-features") && ++featureRequests > 1 && mismatch === "recover") {
      features.machiningRunProcessingRunId = document.processingRunId;
    }
    return new Response(JSON.stringify(url.includes("cycle-features") ? features :
      url.includes("anomaly-assessments") ? assessments : document));
  });
  vi.stubGlobal("fetch", fetch);
  const replayControlClient: ReplayControlClient = {
    load: vi.fn().mockResolvedValue({
      schemaVersion: "1.0.0", machineId: "Mazak01", replaySessionId: document.replaySessionId,
      sourceSetId: "nist-mazak01-20161005", status: "PAUSED", revision: 2,
      speedMultiplier: 10, publicationCursor: snapshot.replayCursor,
      sourceRange: { startsAt: "2016-10-05T05:27:55.740Z", endsAt: "2016-10-05T19:15:07.025Z" },
    }),
    start: vi.fn(), pause: vi.fn(), resume: vi.fn(), changeSpeed: vi.fn(), seek: vi.fn(),
  };
  const state = { connectionStatus: "LIVE" as const, snapshot, freshness: "FRESH" as const };
  const retryTwin = vi.fn();
  const sessionFactory: TwinSessionFactory = () => ({
    start() {}, dispose() {}, retryNow: retryTwin, currentState: () => state,
    subscribe(listener) { listener(state); return () => undefined; },
  });
  render(<MemoryRouter><FactoryRoute sessionFactory={sessionFactory} replayControlClient={replayControlClient}
    sceneLoader={async () => ({ default: () => null })} /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "2D" }));
  return Object.assign(fetch, { retryTwin, replayControlClient });
}

describe("Machine Detail process analysis", () => {
  it("shows an unconfirmed end at the paused cursor without claiming RUNNING", async () => {
    const document = structuredClone(runFixture);
    const run = document.machiningRuns[0];
    run.status = "INTERRUPTED";
    Reflect.deleteProperty(run, "endedAt");
    Reflect.deleteProperty(run, "endEvidence");
    run.confidence.reasons.push("END_BOUNDARY_INCOMPLETE");
    const fetch = showProcess(document);

    const current = await screen.findByRole("region", { name: "CURRENT RUN · 현재 가공" });
    expect(await within(current).findByText("가공 중단 · 종료 근거 미확정")).toBeTruthy();
    expect(within(current).getByText("155")).toBeTruthy();
    expect(within(current).queryByText("RUNNING")).toBeNull();
    expect(JSON.parse(fetch.mock.calls[0][1]?.body as string)).toEqual({
      replaySessionId: document.replaySessionId, throughReplaySequence: 1509,
      segmentationRuleVersion: "1.0.0",
    });
  });

  it("does not fill a gap with the last completed run", async () => {
    showProcess(structuredClone(runFixture));
    const current = await screen.findByRole("region", { name: "CURRENT RUN · 현재 가공" });
    expect(await within(current).findByText("현재 가공 없음")).toBeTruthy();
  });

  it("shows no current run for an empty segmentation result", async () => {
    showProcess({ ...structuredClone(runFixture), machiningRuns: [] });
    expect(await screen.findByText("현재 가공 없음")).toBeTruthy();
  });

  it("shows the completion reason on the timeline instead of a comparison result for an unfinished run", async () => {
    const document = structuredClone(runFixture);
    const run = document.machiningRuns[0];
    run.status = "INTERRUPTED";
    Reflect.deleteProperty(run, "endedAt");
    Reflect.deleteProperty(run, "endEvidence");
    showProcess(document);
    const timelineItem = await screen.findByRole("button", { name: /가공 선택 · 155/ });
    expect(within(timelineItem).getByText("가공 중단 · 종료 근거 미확정")).toBeTruthy();
    expect(within(timelineItem).queryByText("비교 불가")).toBeNull();
  });

  it("explains a selected completed run with channel coverage and insufficient baseline", async () => {
    const fetch = showProcess(structuredClone(runFixture));
    const timelineItem = await screen.findByRole("button", { name: /가공 선택 · 155/ });
    expect(within(timelineItem).getByText("비교 불가")).toBeTruthy();
    expect(within(timelineItem).getByText("0/5")).toBeTruthy();
    fireEvent.click(timelineItem);
    const process = screen.getByRole("region", { name: "PROCESS · 공정 특징" });
    expect(within(process).getByText("120 REVOLUTION/MINUTE")).toBeTruthy();
    expect(within(process).getAllByText(/100%/).length).toBeGreaterThan(0);
    expect(within(process).getAllByText("데이터 없음").length).toBeGreaterThan(0);
    const anomaly = screen.getByRole("region", { name: "ANOMALY · 이전 가공과의 차이" });
    expect(within(anomaly).getByText("비교 표본 부족")).toBeTruthy();
    expect(within(anomaly).getByText(/같은 프로그램 155의 이전 가공이 아직 0건입니다/)).toBeTruthy();
    expect(within(anomaly).queryByText("0%")).toBeNull();
    expect(screen.getByText("OBSERVED · REAL:NIST")).toBeTruthy();
    expect(screen.getByText("startEvidence / sourceEventKey").parentElement?.lastElementChild?.textContent)
      .toBe(runFixture.machiningRuns[0].startEvidence.sourceEventKey);
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/machines/Mazak01/machining-runs/processing-runs",
      "/api/v1/machines/Mazak01/cycle-features/processing-runs",
      "/api/v1/machines/Mazak01/anomaly-assessments/processing-runs",
    ]);
  });

  it("rejects a feature result belonging to another processing run", async () => {
    showProcess(structuredClone(runFixture), true);
    expect(await screen.findByText(/분석 버전이 일치하지 않습니다/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: /가공 선택/ })).toBeNull();
  });

  it("resynchronizes a mismatched bundle and publishes only the recovered version", async () => {
    const { retryTwin } = showProcess(structuredClone(runFixture), "recover");
    expect(await screen.findByText(/분석 버전이 일치하지 않습니다/)).toBeTruthy();
    expect(await screen.findByRole("button", { name: /가공 선택 · 155/ })).toBeTruthy();
    expect(retryTwin).toHaveBeenCalled();
  });

  it("recalculates explicitly and clears the previous selection", async () => {
    const fetch = showProcess(structuredClone(runFixture));
    fireEvent.click(await screen.findByRole("button", { name: /가공 선택 · 155/ }));
    fireEvent.click(screen.getByRole("button", { name: "분석 다시 계산" }));
    expect(screen.queryByRole("region", { name: "PROCESS · 공정 특징" })).toBeNull();
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(6));
    expect(await screen.findByText(/목록에서 가공을 선택/)).toBeTruthy();
  });

  it("selects a run from the timeline without seeking and seeks only on explicit navigation", async () => {
    const { replayControlClient } = showProcess(structuredClone(runFixture));
    fireEvent.click(await screen.findByRole("button", { name: /가공 선택 · 155/ }));
    expect(screen.getByRole("region", { name: "PROCESS · 공정 특징" })).toBeTruthy();
    expect(replayControlClient.seek).not.toHaveBeenCalled();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "가공 시작 시점으로 이동" }));
    });
    expect(replayControlClient.seek).toHaveBeenCalledWith(runFixture.replaySessionId, 2,
      runFixture.machiningRuns[0].startedAt, 10);
  });

  it("also offers an explicit seek to the end of a selected completed run", async () => {
    const { replayControlClient } = showProcess(structuredClone(runFixture));
    fireEvent.click(await screen.findByRole("button", { name: /가공 선택 · 155/ }));
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "가공 종료 시점으로 이동" }));
    });
    expect(replayControlClient.seek).toHaveBeenCalledWith(runFixture.replaySessionId, 2,
      runFixture.machiningRuns[0].endedAt, 10);
  });

  it("shows an anomaly classification on the timeline without opening the run", async () => {
    showProcess(structuredClone(runFixture), false, (assessment) => {
      assessment.dataStatus = "AVAILABLE";
      assessment.classification = "HIGH_DEVIATION";
    });
    const timelineItem = await screen.findByRole("button", { name: /가공 선택 · 155/ });
    expect(within(timelineItem).getByText("큰 차이")).toBeTruthy();
  });

  it("shows the elapsed duration for a completed run on the timeline", async () => {
    showProcess(structuredClone(runFixture));
    const timelineItem = await screen.findByRole("button", { name: /가공 선택 · 155/ });
    expect(within(timelineItem).getByText("2분 29초")).toBeTruthy();
  });

  it("shows a duration comparison against the baseline when one is available", async () => {
    showProcess(structuredClone(runFixture), false, (assessment) => {
      assessment.dataStatus = "AVAILABLE";
      assessment.classification = "DEVIATING";
      assessment.topReasons = [{ featureKey: "durationSeconds", targetValue: 149, baselineMedian: 100,
        difference: 49, percentageDifference: 49, direction: "ABOVE", score: 0.5, sampleCount: 6,
        distance: 1.633333, deviationScale: 30,
        contributingFeatureSetIds: Array.from({ length: 5 }, (_, i) => `sha256:${String(i + 1).repeat(64)}`),
        reasonCode: "BASELINE_IQR_DISTANCE" }];
    });
    const timelineItem = await screen.findByRole("button", { name: /가공 선택 · 155/ });
    const compareText = timelineItem.querySelector(".run-row-compare")?.textContent ?? "";
    expect(compareText).toContain("이번 2분 29초");
    expect(compareText).toContain("기준(중앙값) 1분 40초");
    expect(compareText).toContain("+49%");
    expect(compareText).toContain("정상 폭 30초의 1.6배");
  });

  it("selects the same run from the timeline overview preview", async () => {
    showProcess(structuredClone(runFixture));
    const preview = await screen.findByRole("button", { name: /가공 미리보기 · 155/ });
    fireEvent.click(preview);
    expect(screen.getByRole("region", { name: "PROCESS · 공정 특징" })).toBeTruthy();
    expect(preview.getAttribute("aria-pressed")).toBe("true");
  });

  it("marks the current replay cursor position on the timeline overview", async () => {
    showProcess(structuredClone(runFixture));
    await screen.findByRole("button", { name: /가공 선택 · 155/ });
    expect(screen.getByText(/지금 \d{2}:\d{2}/)).toBeTruthy();
  });
});
