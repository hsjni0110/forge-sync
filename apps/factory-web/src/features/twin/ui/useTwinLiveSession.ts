import { useEffect, useState } from "react";

import {
  type TwinLiveState,
  TwinLiveSession,
} from "../application/TwinLiveSession";

export function useTwinLiveSession(session: TwinLiveSession): TwinLiveState {
  const [state, setState] = useState(() => session.currentState());

  useEffect(() => {
    const unsubscribe = session.subscribe(setState);
    session.start();
    return () => {
      unsubscribe();
      session.dispose();
    };
  }, [session]);

  return state;
}
