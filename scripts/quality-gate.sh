#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "quality-gate: scaffold checks"
./scripts/check-template.sh
./scripts/check-contracts.sh
node scripts/check-scaffold-governance.js
./scripts/collect-demo-evidence.sh --dry-run
./scripts/smoke-copy.sh

echo "quality-gate: backend contract tests"
(
  cd backend
  mvn -q test
  mvn -q -DskipTests package
)

echo "quality-gate: frontend build"
(
  cd frontend
  if [[ -d node_modules/.pnpm ]]; then
    echo "quality-gate: frontend dependencies already present"
  else
    CI=true pnpm install --frozen-lockfile
  fi
  pnpm build
  node --import tsx scripts/clean-dev.ts
)

echo "quality-gate: backend runtime probe"
./scripts/probe-backend-dev.sh

echo "quality-gate: ok"
