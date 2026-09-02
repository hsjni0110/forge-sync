import { useCallback, useEffect, useRef, useState } from "react";

import type { TwinLiveState } from "../application/TwinLiveSession";
import type { TwinSession } from "../application/ports";

export function useTwinLiveSession(createSession: () => TwinSession): {
  state: TwinLiveState;
  retryNow: () => void;
} {
  const [state, setState] = useState<TwinLiveState>({ connectionStatus: "LOADING" });
  const sessionRef = useRef<TwinSession | undefined>(undefined);

  useEffect(() => {
    const session = createSession();
    sessionRef.current = session;
    const unsubscribe = session.subscribe(setState);
    session.start();
    return () => {
      unsubscribe();
      session.dispose();
      if (sessionRef.current === session) {
        sessionRef.current = undefined;
      }
    };
  }, [createSession]);

  const retryNow = useCallback(() => sessionRef.current?.retryNow(), []);
  return { state, retryNow };
}
