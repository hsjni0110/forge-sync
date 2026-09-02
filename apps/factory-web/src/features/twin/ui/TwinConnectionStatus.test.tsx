import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

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
});
