import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import { App } from "./features/shell/ui/App";
import { createBrowserTwinSessionFactory } from "./features/twin/adapters/browserTwinSessionFactory";
import { HttpReplayControlClient } from "./features/replay/adapters/httpReplayControlClient";
import "./styles.css";

const root = document.getElementById("root");
const twinSessionFactory = createBrowserTwinSessionFactory(
  import.meta.env.VITE_API_BASE_URL ?? "",
);
const replayControlClient = new HttpReplayControlClient(
  import.meta.env.VITE_API_BASE_URL ?? "",
);

if (root === null) {
  throw new Error("ForgeSync root element is missing");
}

createRoot(root).render(
  <StrictMode>
    <BrowserRouter>
      <App twinSessionFactory={twinSessionFactory} replayControlClient={replayControlClient} />
    </BrowserRouter>
  </StrictMode>,
);
