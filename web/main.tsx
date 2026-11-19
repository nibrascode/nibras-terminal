import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { NibrasTerminal } from "@/components/nibras-terminal";
import "./app.css";

const root = document.getElementById("root");
if (root) {
  createRoot(root).render(
    <StrictMode>
      <NibrasTerminal />
    </StrictMode>,
  );
}
