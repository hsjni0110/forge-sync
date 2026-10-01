import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

export type OperationalContextTone =
  | "neutral"
  | "positive"
  | "warning"
  | "critical";

export interface OperationalContextValue {
  label: string;
  tone: OperationalContextTone;
}

export interface OperationalContextSnapshot {
  machineId: string;
  connection: OperationalContextValue;
  freshness: OperationalContextValue;
  replay: OperationalContextValue;
}

const NEUTRAL_CONTEXT: OperationalContextSnapshot = {
  machineId: "설비 미선택",
  connection: { label: "연결 확인 중", tone: "neutral" },
  freshness: { label: "최신성 확인 중", tone: "neutral" },
  replay: { label: "재생 정보 없음", tone: "neutral" },
};

interface OperationalContextState {
  revision: number;
  snapshot: OperationalContextSnapshot;
}

interface OperationalContextPort {
  state: OperationalContextState;
  publish: (snapshot: OperationalContextSnapshot) => number;
  restoreNeutral: (ownedRevision: number) => void;
}

const Context = createContext<OperationalContextPort>({
  state: { revision: 0, snapshot: NEUTRAL_CONTEXT },
  publish: () => 0,
  restoreNeutral: () => undefined,
});

export function OperationalContextProvider({ children }: { children: ReactNode }) {
  const revisionRef = useRef(0);
  const [state, setState] = useState<OperationalContextState>({
    revision: 0,
    snapshot: NEUTRAL_CONTEXT,
  });

  const publish = useCallback((snapshot: OperationalContextSnapshot) => {
    const revision = revisionRef.current + 1;
    revisionRef.current = revision;
    setState({ revision, snapshot });
    return revision;
  }, []);

  const restoreNeutral = useCallback((ownedRevision: number) => {
    setState((current) =>
      current.revision === ownedRevision
        ? { revision: current.revision, snapshot: NEUTRAL_CONTEXT }
        : current,
    );
  }, []);

  const value = useMemo(
    () => ({ state, publish, restoreNeutral }),
    [publish, restoreNeutral, state],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useOperationalContextPublisher(snapshot: OperationalContextSnapshot) {
  const { publish, restoreNeutral } = useContext(Context);
  const {
    machineId,
    connection,
    freshness,
    replay,
  } = snapshot;

  useEffect(() => {
    const ownedRevision = publish({ machineId, connection, freshness, replay });
    return () => restoreNeutral(ownedRevision);
  }, [
    connection.label,
    connection.tone,
    freshness.label,
    freshness.tone,
    machineId,
    publish,
    replay.label,
    replay.tone,
    restoreNeutral,
  ]);
}

export function useOperationalContextSnapshot() {
  return useContext(Context).state.snapshot;
}
