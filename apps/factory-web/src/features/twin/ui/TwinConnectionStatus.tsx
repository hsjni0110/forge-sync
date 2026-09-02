import { useMemo } from "react";

import {
  type TwinLiveState,
  TwinLiveSession,
} from "../application/TwinLiveSession";
import { browserClock, browserTimer } from "../adapters/browserRuntime";
import { BrowserTwinSocketFactory } from "../adapters/browserTwinSocketFactory";
import { HttpTwinSnapshotReader } from "../adapters/httpTwinSnapshotReader";
import { AjvTwinPatchDecoder } from "../adapters/twinContract";
import { useTwinLiveSession } from "./useTwinLiveSession";

interface TwinConnectionStatusViewProps {
  state: TwinLiveState;
}

export function TwinConnectionStatusView({ state }: TwinConnectionStatusViewProps) {
  return (
    <section aria-label="Twin connection status">
      <dl>
        <div>
          <dt>Connection</dt>
          <dd>{state.connectionStatus}</dd>
        </div>
        <div>
          <dt>Consistency</dt>
          <dd>{state.snapshot?.consistency.status ?? "UNAVAILABLE"}</dd>
        </div>
        <div>
          <dt>Freshness</dt>
          <dd>{state.freshness ?? "UNAVAILABLE"}</dd>
        </div>
      </dl>
    </section>
  );
}

interface TwinConnectionStatusProps {
  machineId: string;
  apiBaseUrl?: string;
}

export function TwinConnectionStatus({
  machineId,
  apiBaseUrl = "",
}: TwinConnectionStatusProps) {
  const session = useMemo(
    () =>
      new TwinLiveSession(
        machineId,
        new HttpTwinSnapshotReader(apiBaseUrl),
        new BrowserTwinSocketFactory(apiBaseUrl || window.location.origin),
        new AjvTwinPatchDecoder(),
        browserClock,
        browserTimer,
      ),
    [apiBaseUrl, machineId],
  );
  const state = useTwinLiveSession(session);
  return <TwinConnectionStatusView state={state} />;
}
