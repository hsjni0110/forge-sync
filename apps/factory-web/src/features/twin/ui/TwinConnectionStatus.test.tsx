import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TwinConnectionStatusView } from "./TwinConnectionStatus";

describe("TwinConnectionStatusView", () => {
  it("shows connection, consistency, and freshness as separate states", () => {
    render(
      <TwinConnectionStatusView
        state={{ connectionStatus: "RECONNECTING", freshness: "STALE" }}
      />,
    );

    expect(screen.getByText("RECONNECTING")).toBeTruthy();
    expect(screen.getAllByText("UNAVAILABLE")).toHaveLength(1);
    expect(screen.getByText("STALE")).toBeTruthy();
  });
});
