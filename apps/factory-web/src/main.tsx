import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { App } from "./features/shell/ui/App";
import "./styles.css";

const root = document.getElementById("root");

if (root === null) {
  throw new Error("ForgeSync root element is missing");
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
