#!/usr/bin/env bash
# Ensures the local PostgreSQL (port 5433) is running for tests.
# The database must live in the same process tree as the test run in this
# sandbox, so this is sourced by the test scripts rather than run standalone.
set -e
PG_BIN="/c/Users/Aakash/pg16/bin"
PG_DATA="C:/Users/Aakash/pg16/data"
PG_LOG="C:/Users/Aakash/pg16/pg.log"

if "$PG_BIN/pg_isready.exe" -p 5433 >/dev/null 2>&1; then
  echo "postgres: already running on 5433"
  exit 0
fi

echo "postgres: starting on 5433"
"$PG_BIN/pg_ctl.exe" -D "$PG_DATA" -l "$PG_LOG" -o "-p 5433" start > /dev/null 2>&1 || true

for _ in $(seq 1 30); do
  if "$PG_BIN/pg_isready.exe" -p 5433 >/dev/null 2>&1; then
    echo "postgres: ready"
    exit 0
  fi
  sleep 1
done

echo "postgres: failed to start (see $PG_LOG)" >&2
exit 1
