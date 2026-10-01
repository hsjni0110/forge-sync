import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import { App } from "./features/shell/ui/App";
import { createBrowserTwinSessionFactory } from "./features/twin/adapters/browserTwinSessionFactory";
import { HttpReplayControlClient } from "./features/replay/adapters/httpReplayControlClient";
import { HttpToolChangeClient } from "./features/tool-change/adapters/httpToolChangeClient";
import { HttpObservedToolpathClient } from "./features/toolpath/adapters/httpObservedToolpathClient";
import { HttpShiftOverviewClient } from "./features/shift-overview/adapters/httpShiftOverviewClient";
import { HttpAlarmClient } from "./features/alarm/adapters/httpAlarmClient";
import { HttpDataQualityClient } from "./features/data-quality/adapters/httpDataQualityClient";
import "./styles/index.scss";

const root = document.getElementById("root");
const twinSessionFactory = createBrowserTwinSessionFactory(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
const replayControlClient = new HttpReplayControlClient(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
const toolChangeClient = new HttpToolChangeClient(import.meta.env.VITE_API_BASE_URL ?? "");
const observedToolpathClient = new HttpObservedToolpathClient(import.meta.env.VITE_API_BASE_URL ?? "");
const shiftOverviewClient = new HttpShiftOverviewClient(import.meta.env.VITE_API_BASE_URL ?? "");
const alarmClient = new HttpAlarmClient(import.meta.env.VITE_API_BASE_URL ?? "");
const dataQualityClient = new HttpDataQualityClient(import.meta.env.VITE_API_BASE_URL ?? "");

if (root === null) {
  throw new Error("ForgeSync root element is missing");
}

createRoot(root).render(
  <StrictMode>
    <BrowserRouter>
      <App twinSessionFactory={twinSessionFactory} replayControlClient={replayControlClient}
        toolChangeClient={toolChangeClient} observedToolpathClient={observedToolpathClient}
        shiftOverviewClient={shiftOverviewClient} alarmClient={alarmClient}
        dataQualityClient={dataQualityClient} />
    </BrowserRouter>
  </StrictMode>,
);
