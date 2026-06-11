#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "quality-gate: scaffold checks"
./scripts/check-template.sh
./scripts/check-contracts.sh
./scripts/smoke-copy.sh

echo "quality-gate: backend contract tests"
(
  cd backend
  mvn -q -Dtest=RequestContextTaskDecoratorTest test
  mvn -q -Dtest=LocaleUtilsTest test
  mvn -q -Dtest=TimeZoneUtilsTest test
  mvn -q -Dtest=JsonUtilsTest test
  mvn -q -Dtest=RemoteCallWrapperContextHeadersTest test
  mvn -q -Dtest=RemoteHttpClientTest test
  mvn -q -DskipTests package
)

echo "quality-gate: frontend build"
(
  cd frontend
  pnpm install --frozen-lockfile
  pnpm build
  pnpm -s clean:dev
)

echo "quality-gate: backend runtime probe"
./scripts/probe-backend-dev.sh

echo "quality-gate: ok"
