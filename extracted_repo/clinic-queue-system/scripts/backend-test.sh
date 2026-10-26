#!/usr/bin/env bash
# Backend test runner: unit tests (surefire) then PostgreSQL integration tests (failsafe).
# Usage: ./scripts/backend-test.sh [unit|it|all]
set -e
MODE="${1:-all}"
# The shell default JAVA_HOME is a JRE 8; force the JDK 21 used to build.
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.101-hotspot"
bash "$(dirname "$0")/ensure-postgres.sh"
cd "$(dirname "$0")/../backend"

case "$MODE" in
  unit)
    mvn test -Dsurefire.printSummary=true
    ;;
  it)
    mvn verify -DskipUnitTests=true -Dsurefire.skip=true
    ;;
  *)
    mvn verify -DskipUnitTests=false
    ;;
esac
