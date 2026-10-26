import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      // Keep a same-origin fallback for local dev; the app normally talks to
      // the API directly via VITE_API_BASE_URL.
      "/api": {
        target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
});
