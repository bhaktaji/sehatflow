import { defineConfig, devices } from "@playwright/test";

const JAVA = "C:/Program Files/Microsoft/jdk-21.0.12.101-hotspot/bin/java.exe";
const BACKEND_PORT = 8090;
const FRONTEND_PORT = 5180;

const reuse = process.env.E2E_REUSE_SERVERS === "1";

export default defineConfig({
  testDir: "./specs",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  workers: 1, // single shared backend + dev server
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: "off",
    screenshot: "off",
    video: "off",
    // The sandbox has an HTTP proxy configured; Chromium must bypass it for localhost.
    launchOptions: { args: ["--no-proxy-server"] },
  },
  webServer: [
    {
      command: `"${JAVA}" -jar ../backend/target/clinic-queue-backend-0.1.0.jar --server.port=${BACKEND_PORT}`,
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      reuseExistingServer: reuse,
      timeout: 120_000,
      stdout: "pipe",
      stderr: "pipe",
    },
    {
      command: "npm run dev",
      cwd: "../frontend",
      url: `http://localhost:${FRONTEND_PORT}`,
      reuseExistingServer: reuse,
      timeout: 120_000,
    },
  ],
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
