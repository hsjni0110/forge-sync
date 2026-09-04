import { useEffect, useRef } from "react";

import type { TwinLiveState } from "../../twin/application/TwinLiveSession";
import type { ReplayStatus } from "../domain/replay";

const TWIN_BOOTSTRAP_RETRY_DELAYS_MILLIS = [250, 500, 1_000, 2_000, 4_000] as const;

export function useReplayDrivenTwinBootstrap(
  replayStatus: ReplayStatus | undefined,
  twinState: TwinLiveState,
  retryTwin: () => void,
): void {
  const retryAttempt = useRef(0);

  useEffect(() => {
    if (replayStatus !== "RUNNING" || twinState.snapshot) {
      retryAttempt.current = 0;
      return;
    }
    if (
      twinState.connectionStatus !== "UNAVAILABLE" ||
      twinState.failure !== "NOT_FOUND" ||
      retryAttempt.current >= TWIN_BOOTSTRAP_RETRY_DELAYS_MILLIS.length
    ) {
      return;
    }

    const delayMillis = TWIN_BOOTSTRAP_RETRY_DELAYS_MILLIS[retryAttempt.current];
    const timerId = window.setTimeout(() => {
      retryAttempt.current += 1;
      retryTwin();
    }, delayMillis);
    return () => window.clearTimeout(timerId);
  }, [replayStatus, retryTwin, twinState.connectionStatus, twinState.failure, twinState.snapshot]);
}
