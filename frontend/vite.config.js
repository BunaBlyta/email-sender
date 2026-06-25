import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backend = "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/inbox": backend,
      "/send": backend,
      "/templates": backend,
      "/scheduled": backend,
      "/recipient-groups": backend,
      "/drafts": backend,
      "/security": backend,
      "/screener": backend,
      "/account": backend,
      "/tracking": backend,
      "/track": backend,
      "/oauth2": backend,
      "/login": backend,
      "/logout": backend
    }
  },
  build: {
    outDir: "dist",
    emptyOutDir: true
  }
});
