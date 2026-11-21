import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";

const src = fileURLToPath(new URL("../src", import.meta.url));
const pkg = (name: string) => fileURLToPath(new URL(`./node_modules/${name}`, import.meta.url));

export default defineConfig({
  base: "/nibras-terminal/",
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: [
      { find: "@/lib/terminal/remote.functions", replacement: fileURLToPath(new URL("./remote-stub.ts", import.meta.url)) },
      { find: "@", replacement: src },
      { find: "react/jsx-dev-runtime", replacement: pkg("react/jsx-dev-runtime.js") },
      { find: "react/jsx-runtime", replacement: pkg("react/jsx-runtime.js") },
      { find: "react-dom/client", replacement: pkg("react-dom/client.js") },
      { find: /^react-dom$/, replacement: pkg("react-dom") },
      { find: /^react$/, replacement: pkg("react") },
      { find: /^lucide-react$/, replacement: pkg("lucide-react") },
      { find: "libsodium-wrappers", replacement: pkg("libsodium-wrappers") },
      { find: "node-forge", replacement: pkg("node-forge") },
    ],
  },
});
