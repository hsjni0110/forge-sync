import { useCallback, useEffect, useRef, useState } from "react";
import type { ReplaySessionState } from "../../replay/domain/replay";
import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { Timer } from "../../twin/application/ports";
import { browserTimer } from "../../twin/adapters/browserRuntime";
import { ProcessAnalysisError, type ProcessAnalysisClient } from "../application/ports";
import { hasMatchingWatermark, type RunAnalysis } from "../domain/processAnalysis";

const RESYNC_DELAYS_MILLIS = [250, 500, 1000, 2000, 4000];
interface AnalysisState { key: string; analysis?: RunAnalysis; message?: string; failed?: boolean }

export function useProcessAnalysis({ machineId, session, twinState, client, retryTwin, reloadReplay, timer = browserTimer }: {
  machineId: string; session?: ReplaySessionState; twinState: TwinLiveState;
  client: ProcessAnalysisClient; retryTwin: () => void; reloadReplay: () => Promise<void>; timer?: Timer;
}) {
  const cursor = twinState.snapshot?.replayCursor;
  const stopped = !!session && session.machineId === machineId && ["PAUSED", "COMPLETED"].includes(session.status);
  const ready = stopped && !!cursor && twinState.connectionStatus === "LIVE" &&
    twinState.snapshot?.machine.machineId === machineId &&
    twinState.snapshot.consistency.twinVersion === cursor.twinVersion && hasMatchingWatermark(cursor, session);
  const cursorKey = JSON.stringify([machineId, cursor]);
  const [revision, setRevision] = useState(0);
  const key = `${cursorKey}:${revision}`;
  const [state, setState] = useState<AnalysisState>();
  const retryCount = useRef({ cursorKey, count: 0 });
  const retry = useCallback(() => {
    retryCount.current.count = 0;
    setRevision((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!stopped) return;
    if (retryCount.current.cursorKey !== cursorKey) retryCount.current = { cursorKey, count: 0 };
    const controller = new AbortController();
    let timerId: number | undefined;
    const resync = () => {
      const delay = RESYNC_DELAYS_MILLIS[retryCount.current.count];
      setState({ key, failed: delay === undefined,
        message: "분석 버전이 일치하지 않습니다. 재생 시점과 다시 동기화 중입니다." });
      if (delay === undefined) return;
      retryCount.current.count += 1;
      timerId = timer.schedule(() => {
        retryTwin();
        void reloadReplay().finally(() => {
          if (!controller.signal.aborted) setRevision((value) => value + 1);
        });
      }, delay);
    };
    if (!ready || !cursor) {
      resync();
    } else {
      setState({ key, message: revision ? "공정 분석 재계산 중" : "공정 분석 중" });
      void client.analyze(machineId, cursor, controller.signal).then((analysis) => {
        if (!controller.signal.aborted) {
          retryCount.current.count = 0;
          setState({ key, analysis });
        }
      }).catch((failure: unknown) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ProcessAnalysisError && failure.code === "VERSION_MISMATCH") resync();
        else setState({ key, failed: true, message: failure instanceof ProcessAnalysisError && failure.code === "INPUT_NOT_FOUND"
          ? "분석할 관측 입력 없음" : "공정 분석을 불러올 수 없습니다. 다시 시도해 주세요." });
      });
    }
    return () => { controller.abort(); if (timerId !== undefined) timer.cancel(timerId); };
  }, [key, cursorKey, ready, stopped, client, machineId, retryTwin, reloadReplay, timer]);

  const visible = state?.key === key ? state : undefined;
  return {
    analysis: ready ? visible?.analysis : undefined,
    message: !stopped ? "일시정지하면 해당 시점의 공정 분석을 확인할 수 있습니다" : visible?.message ?? "공정 분석 중",
    canRetry: stopped && (!!visible?.analysis || !!visible?.failed), retry,
  };
}
