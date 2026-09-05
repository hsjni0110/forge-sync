import { useCallback, useEffect, useRef, useState } from "react";
import type { ReplayControlClient } from "../application/ports";
import type { ReplaySessionState, ReplayStatus } from "../domain/replay";
import type { Timer } from "../../twin/application/ports";
import { browserTimer } from "../../twin/adapters/browserRuntime";

interface ReplayControlState {
  machineId: string;
  session?: ReplaySessionState;
  confirmedSession?: ReplaySessionState;
  isLoading: boolean;
  isCommandPending: boolean;
  hasLoadFailure: boolean;
  error?: string;
}

export type ReplayController = ReturnType<typeof useReplayController>;

export function useReplayController(machineId: string, client?: ReplayControlClient, timer: Timer = browserTimer) {
  const [state, setState] = useState<ReplayControlState>({ machineId,
    isLoading: true, isCommandPending: false, hasLoadFailure: false });
  const generation = useRef(0);
  const commandPending = useRef(false);

  const reload = useCallback(async () => {
    if (!client || commandPending.current) return;
    const request = ++generation.current;
    try {
      const session = await client.load(machineId);
      if (session && session.machineId !== machineId) throw new Error("Replay machine mismatch");
      if (request === generation.current) setState({ machineId, session, confirmedSession: session,
        isLoading: false, isCommandPending: false, hasLoadFailure: false });
    } catch {
      if (request === generation.current) setState((previous) => ({ ...previous, isLoading: false,
        hasLoadFailure: true, error: "Replay 상태를 불러오지 못했습니다. 서버 연결을 확인한 뒤 다시 시도해 주세요." }));
    }
  }, [client, machineId]);

  useEffect(() => {
    commandPending.current = false;
    setState({ machineId, isLoading: !!client, isCommandPending: false, hasLoadFailure: false });
    void reload();
    return () => { generation.current += 1; };
  }, [machineId, client, reload]);

  useEffect(() => {
    if (state.isCommandPending || !state.session || !["RUNNING", "SEEKING", "PREPARING"].includes(state.session.status)) return;
    const id = timer.schedule(() => void reload(), state.session.status === "RUNNING" ? 1000 : 250);
    return () => timer.cancel(id);
  }, [state, reload, timer]);

  const run = async (optimisticStatus: ReplayStatus,
    action: (current: ReplaySessionState) => Promise<ReplaySessionState>) => {
    if (!client || !state.session || commandPending.current) return;
    const previous = state.session;
    commandPending.current = true;
    const request = ++generation.current;
    setState((value) => ({ ...value, isCommandPending: true, error: undefined,
      session: { ...previous, status: optimisticStatus } }));
    try {
      const session = await action(previous);
      if (session.machineId !== machineId) throw new Error("Replay machine mismatch");
      if (request === generation.current) setState({ machineId, session, confirmedSession: session,
        isLoading: false, isCommandPending: false, hasLoadFailure: false });
    } catch {
      let session = previous;
      let hasLoadFailure = false;
      try { session = await client.load(machineId) ?? previous; } catch { hasLoadFailure = true; }
      if (request === generation.current) setState({ machineId, session, confirmedSession: session,
        isLoading: false, isCommandPending: false, hasLoadFailure,
        error: "서버가 명령을 받지 않았습니다. 권위 상태로 되돌렸습니다." });
    } finally {
      if (request === generation.current) commandPending.current = false;
    }
  };

  const start = async () => {
    if (!client || commandPending.current) return;
    commandPending.current = true;
    const request = ++generation.current;
    setState((value) => ({ ...value, isLoading: true, isCommandPending: true, error: undefined }));
    try {
      const session = await client.start(machineId, "nist-mazak01-20161005", 10);
      if (request === generation.current) setState({ machineId, session, confirmedSession: session,
        isLoading: false, isCommandPending: false, hasLoadFailure: false });
    } catch {
      if (request === generation.current) setState((value) => ({ ...value, isLoading: false,
        isCommandPending: false, hasLoadFailure: true, error: "Replay를 시작할 수 없습니다." }));
    } finally {
      if (request === generation.current) commandPending.current = false;
    }
  };

  const matchesMachine = state.machineId === machineId;
  return { ...state, session: matchesMachine ? state.session : undefined,
    authoritativeSession: matchesMachine && !state.isCommandPending && !state.hasLoadFailure
      ? state.confirmedSession : undefined,
    reload, run, start };
}
