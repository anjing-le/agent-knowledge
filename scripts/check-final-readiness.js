#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

function fail(message) {
  console.error(`check-final-readiness: ${message}`)
  process.exit(1)
}

function read(relativeFile) {
  const file = path.join(root, relativeFile)
  if (!fs.existsSync(file)) {
    fail(`missing required file: ${relativeFile}`)
  }
  return fs.readFileSync(file, 'utf8')
}

function requireToken(relativeFile, token) {
  const source = read(relativeFile)
  if (!source.includes(token)) {
    fail(`${relativeFile} is missing token: ${token}`)
  }
}

for (const file of [
  'project_document/FINAL_READINESS_AUDIT.md',
  'README.md',
  'project_document/README.md',
  'project_document/STATUS.md',
  'scripts/quality-gate.sh',
  'scripts/check-teaching-handoff.sh',
  'scripts/collect-demo-evidence.sh',
  'scripts/probe-rag-demo-runtime.sh',
  'scripts/probe-rag-ingestion-runtime.sh',
  'contracts/scaffold-stack-contract.json',
  'contracts/doc-parser-contract.json',
  'contracts/retrieval-adapter-contract.json'
]) {
  read(file)
}

for (const token of [
  'infra-dev-scaffolding',
  'V1 teaching baseline',
  'RAG 主链路',
  'Java 后端',
  'Python doc-parser',
  './scripts/quality-gate.sh',
  './scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force',
  'docs/evidence/YYYY-MM-DD/',
  'V2/V3 extension',
  'No-Go'
]) {
  requireToken('project_document/FINAL_READINESS_AUDIT.md', token)
}

for (const token of [
  'project_document/FINAL_READINESS_AUDIT.md',
  'project_document/PROJECT_CONSTRAINTS.md',
  'project_document/DEMO_EVIDENCE.md',
  './scripts/quality-gate.sh',
  './scripts/check-teaching-handoff.sh'
]) {
  requireToken('README.md', token)
}

for (const token of [
  'EXPECTED_EMAIL="245548353+anjing-le@users.noreply.github.com"',
  'v1-teaching-baseline',
  'v1.1-teaching-handoff',
  'git ls-remote --heads origin main master',
  'gh run list --limit 8',
  'docs/evidence',
  'V1.1 teaching handoff is ready'
]) {
  requireToken('scripts/check-teaching-handoff.sh', token)
}

for (const token of [
  'FINAL_READINESS_AUDIT.md',
  'V1 teaching baseline',
  'quality-gate.sh'
]) {
  requireToken('project_document/README.md', token)
}

for (const token of [
  'node scripts/check-final-readiness.js',
  './scripts/collect-demo-evidence.sh --dry-run'
]) {
  requireToken('scripts/quality-gate.sh', token)
}

for (const token of [
  'probe-rag-demo-runtime: ok',
  '/api/test/rag-demo/evidence-report'
]) {
  requireToken('scripts/probe-rag-demo-runtime.sh', token)
}

for (const token of [
  'DOC_PARSER_PYTHON',
  'Python FastAPI doc-parser',
  'probe-rag-ingestion-runtime: ok'
]) {
  requireToken('scripts/probe-rag-ingestion-runtime.sh', token)
}

console.log('check-final-readiness: ok')
