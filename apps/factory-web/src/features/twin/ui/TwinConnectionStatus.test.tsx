import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import twinFixture from "../../../../../../tests/fixtures/twin/v1/mazak01-operational-twin.json";
import type { TwinSnapshot } from "../domain/twin";
import { TwinConnectionStatus } from "./TwinConnectionStatus";

describe("TwinConnectionStatusView", () => {
  it("shows connection, consistency, and freshness as separate states", () => {
    render(
      <TwinConnectionStatus
        state={{ connectionStatus: "RECONNECTING", freshness: "STALE" }}
      />,
    );

    expect(screen.getByText("다시 연결 중")).toBeTruthy();
    expect(screen.getAllByText("확인할 수 없음")).toHaveLength(1);
    expect(screen.getByText("오래된 데이터")).toBeTruthy();
  });

  it("explains the twin version in operational language", () => {
    render(
      <TwinConnectionStatus
        state={{
          connectionStatus: "LIVE",
          freshness: "FRESH",
          snapshot: structuredClone(twinFixture) as unknown as TwinSnapshot,
        }}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "트윈 버전 도움말" }));

    expect(screen.getByText("설비 상태가 갱신된 순서입니다.")).toBeTruthy();
  });
});
