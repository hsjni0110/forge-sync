import { TwinLiveSession } from "../application/TwinLiveSession";
import type { TwinSessionFactory } from "../application/ports";
import { browserClock, browserTimer } from "./browserRuntime";
import { BrowserTwinSocketFactory } from "./browserTwinSocketFactory";
import { HttpTwinSnapshotReader } from "./httpTwinSnapshotReader";
import { AjvTwinPatchDecoder } from "./twinContract";

export function createBrowserTwinSessionFactory(apiBaseUrl = ""): TwinSessionFactory {
  return (machineId) =>
    new TwinLiveSession(
      machineId,
      new HttpTwinSnapshotReader(apiBaseUrl),
      new BrowserTwinSocketFactory(apiBaseUrl || window.location.origin),
      new AjvTwinPatchDecoder(),
      browserClock,
      browserTimer,
    );
}
