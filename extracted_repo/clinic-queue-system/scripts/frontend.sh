#!/usr/bin/env bash
# Frontend helper. Usage: ./scripts/frontend.sh [lint|test|build|dev|preview] [extra args]
set -e
MODE="${1:-build}"
shift || true
cd "$(dirname "$0")/../frontend"

case "$MODE" in
  lint) npm run lint "$@" ;;
  test) npm run test "$@" ;;
  build) npm run build "$@" ;;
  dev) npm run dev "$@" ;;
  preview) npm run preview "$@" ;;
  install) npm install "$@" ;;
  *) echo "unknown mode: $MODE" >&2; exit 2 ;;
esac
