import { cleanup, render, screen, waitFor } from "@testing-library/react";
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
    expect(screen.getAllByText("측정 불가").length).toBeGreaterThan(0);
    expect(screen.queryByText("정상")).toBeNull();
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
