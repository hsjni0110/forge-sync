import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import { App } from "./features/shell/ui/App";
import { createBrowserTwinSessionFactory } from "./features/twin/adapters/browserTwinSessionFactory";
import { HttpReplayControlClient } from "./features/replay/adapters/httpReplayControlClient";
import { HttpToolChangeClient } from "./features/tool-change/adapters/httpToolChangeClient";
import { HttpObservedToolpathClient } from "./features/toolpath/adapters/httpObservedToolpathClient";
import { HttpDowntimeParetoClient } from "./features/downtime/adapters/httpDowntimeParetoClient";
import { HttpOperationalEffectivenessClient } from "./features/effectiveness/adapters/httpOperationalEffectivenessClient";
import "./styles.css";

const root = document.getElementById("root");
const twinSessionFactory = createBrowserTwinSessionFactory(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
const replayControlClient = new HttpReplayControlClient(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
const toolChangeClient = new HttpToolChangeClient(import.meta.env.VITE_API_BASE_URL ?? "");
const observedToolpathClient = new HttpObservedToolpathClient(import.meta.env.VITE_API_BASE_URL ?? "");
const downtimeParetoClient = new HttpDowntimeParetoClient(import.meta.env.VITE_API_BASE_URL ?? "");
const operationalEffectivenessClient = new HttpOperationalEffectivenessClient(import.meta.env.VITE_API_BASE_URL ?? "");

if (root === null) {
  throw new Error("ForgeSync root element is missing");
}

createRoot(root).render(
  <StrictMode>
    <BrowserRouter>
      <App twinSessionFactory={twinSessionFactory} replayControlClient={replayControlClient}
        toolChangeClient={toolChangeClient} observedToolpathClient={observedToolpathClient}
        downtimeParetoClient={downtimeParetoClient}
        operationalEffectivenessClient={operationalEffectivenessClient} />
    </BrowserRouter>
  </StrictMode>,
);
