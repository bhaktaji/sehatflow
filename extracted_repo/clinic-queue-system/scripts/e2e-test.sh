#!/usr/bin/env bash
# Playwright E2E runner. Usage: ./scripts/e2e-test.sh [playwright args...]
set -e
cd "$(dirname "$0")/../e2e"
bash "$(dirname "$0")/ensure-postgres.sh"
# Ensure the backend jar is current unless SKIP_BUILD is set.
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.101-hotspot"
  (cd ../backend && mvn -q -DskipTests package)
fi
# Clear stale servers so the Playwright-managed webServers bind cleanly.
# Only kills THIS project's vite (other projects may share port 5173).
taskkill //F //IM java.exe 2>/dev/null || true
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | Where-Object { \$_.CommandLine -like '*clinic-queue*vite*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null || true
sleep 1
exec npx playwright test "$@"
