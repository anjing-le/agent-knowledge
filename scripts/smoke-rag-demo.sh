#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="${TMPDIR:-/tmp}"
LOG_FILE="$TMP_ROOT/agent-knowledge-rag-demo-smoke.log"

rm -f "$LOG_FILE"

if ! (
  cd "$ROOT/backend"
  SPRING_PROFILES_ACTIVE=test mvn -q -Dtest=RagDemoSmokeTest test >"$LOG_FILE" 2>&1
); then
  echo "smoke-rag-demo: failed" >&2
  tail -n 180 "$LOG_FILE" >&2 || true
  exit 1
fi

echo "smoke-rag-demo: ok"
echo "smoke-rag-demo: log=$LOG_FILE"
