import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import fixture from "../../../../../../tests/fixtures/data-quality/v1/mazak01-data-quality.json";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { DataQualityClient } from "../application/ports";
import type { DataQualityReport } from "../domain/dataQuality";
import { DataQualityRoute, DataQualityView } from "./DataQualityRoute";

afterEach(cleanup);

describe("DataQualityView", () => {
  it("shows six separate dimensions and never turns missing evidence into a normal grade", () => {
    render(<DataQualityView report={structuredClone(fixture) as DataQualityReport} />);

    for (const heading of ["유효성", "완전성", "순서", "중복", "최신성", "의미 변환률"]) {
      expect(screen.getByRole("heading", { name: heading })).toBeTruthy();
    }
    expect(screen.getByText("종합 품질 점수 없음")).toBeTruthy();
    expect(screen.getByText("87.67%")).toBeTruthy();
    expect(screen.getAllByText("평가할 수 없음").length).toBeGreaterThan(0);
    expect(screen.queryByText("정상")).toBeNull();
    const groups = ["원천과 의미 매핑", "수신과 순서", "데이터 최신성", "파생 분석 범위", "미해석 원천 항목"];
    for (const group of groups) {
      expect(screen.getByRole("region", { name: group })).toBeTruthy();
    }
    const source = screen.getByRole("region", { name: "원천과 의미 매핑" });
    const runtime = screen.getByRole("region", { name: "수신과 순서" });
    expect(source.compareDocumentPosition(runtime) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(source).getByRole("button", { name: "출처와 계보 도움말" })).toBeTruthy();
    expect(screen.getByText("특징값 확보 범위")).toBeTruthy();
  });

  it("keeps every not-evaluated derived reason visible without inventing a score", () => {
    const report = structuredClone(fixture) as DataQualityReport;
    report.derivedProcess.segmentation.status = "NOT_EVALUATED";
    report.derivedProcess.segmentation.reason = "재생 범위가 선택되지 않았습니다.";
    report.derivedProcess.featureCoverage.status = "NOT_EVALUATED";
    report.derivedProcess.featureCoverage.reason = "가공 구간 근거가 없습니다.";

    render(<DataQualityView report={report} />);

    const derived = screen.getByRole("region", { name: "파생 분석 범위" });
    expect(within(derived).getAllByText("평가할 수 없음")).toHaveLength(2);
    expect(within(derived).getByText("재생 범위가 선택되지 않았습니다.")).toBeTruthy();
    expect(within(derived).getByText("가공 구간 근거가 없습니다.")).toBeTruthy();
    expect(derived.textContent).not.toMatch(/%|정상|100/);
  });

  it("links every unmapped item to its preserved raw locator", () => {
    render(<DataQualityView report={structuredClone(fixture) as DataQualityReport} />);

    const link = screen.getByRole("link", { name: "원본 줄 보기" });
    expect(link.getAttribute("href")).toContain("#L5");
    expect(link.getAttribute("title")).toContain("#bytes=");
  });
});

describe("DataQualityRoute", () => {
  it("shows source evidence before waiting for a busy Replay cursor to settle", async () => {
    const report = structuredClone(fixture) as DataQualityReport;
    report.replaySessionId = null;
    report.throughReplaySequence = null;
    const load = vi.fn<DataQualityClient["load"]>(async () => report);
    const client: DataQualityClient = { load };
    const state = {
      connectionStatus: "LIVE",
      snapshot: {
        replayCursor: {
          replaySessionId: "00d64db8-967e-41ba-9d09-fdd087710aac",
          replaySequence: 42,
        },
      },
    } as unknown as TwinLiveState;
    const twinSessionFactory: TwinSessionFactory = () => ({
      start: () => undefined,
      dispose: () => undefined,
      retryNow: () => undefined,
      currentState: () => state,
      subscribe: (listener) => {
        listener(state);
        return () => undefined;
      },
    });

    render(<DataQualityRoute client={client} twinSessionFactory={twinSessionFactory} />);

    await waitFor(() => expect(screen.getByText("종합 품질 점수 없음")).toBeTruthy());
    expect(load.mock.calls[0]?.[1]).toBeUndefined();
  });
});
