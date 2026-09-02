import { cleanup, render, screen } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { TwinLiveState } from "../application/TwinLiveSession";
import type { TwinSession } from "../application/ports";
import { useTwinLiveSession } from "./useTwinLiveSession";

afterEach(cleanup);

function SessionConsumer({ createSession }: { createSession: () => TwinSession }) {
  const { state } = useTwinLiveSession(createSession);
  return <output>{state.connectionStatus}</output>;
}

describe("useTwinLiveSession", () => {
  it("creates a fresh session when StrictMode remounts the effect", () => {
    const sessions: TwinSession[] = [];
    const createSession = vi.fn(() => {
      let listener: ((state: TwinLiveState) => void) | undefined;
      const session: TwinSession = {
        start: () => listener?.({ connectionStatus: "LIVE" }),
        subscribe: (nextListener) => {
          listener = nextListener;
          nextListener({ connectionStatus: "LOADING" });
          return () => {
            listener = undefined;
          };
        },
        currentState: () => ({ connectionStatus: "LOADING" }),
        retryNow: vi.fn(),
        dispose: vi.fn(),
      };
      sessions.push(session);
      return session;
    });

    render(
      <StrictMode>
        <SessionConsumer createSession={createSession} />
      </StrictMode>,
    );

    expect(createSession).toHaveBeenCalledTimes(2);
    expect(sessions[0].dispose).toHaveBeenCalledOnce();
    expect(sessions[1].dispose).not.toHaveBeenCalled();
    expect(screen.getByText("LIVE")).toBeTruthy();
  });
});
