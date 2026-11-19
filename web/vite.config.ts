import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";

const src = fileURLToPath(new URL("../src", import.meta.url));

export default defineConfig({
  base: "/nibras-terminal/",
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: [
      { find: "@/lib/terminal/remote.functions", replacement: fileURLToPath(new URL("./remote-stub.ts", import.meta.url)) },
      { find: "@", replacement: src },
    ],
  },
});
