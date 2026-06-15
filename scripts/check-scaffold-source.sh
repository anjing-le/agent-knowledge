#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

node <<'NODE'
const fs = require('fs')
const path = require('path')

function fail(message) {
  console.error(`check-scaffold-source: ${message}`)
  process.exit(1)
}

function requireToken(file, source, token) {
  if (!source.includes(token)) {
    fail(`${file} is missing token: ${token}`)
  }
}

function requireAnyToken(file, source, tokens) {
  if (!tokens.some((token) => source.includes(token))) {
    fail(`${file} is missing one of tokens: ${tokens.join(' | ')}`)
  }
}

function requireStartsWith(actual, prefix, label) {
  if (!actual || !actual.startsWith(prefix)) {
    fail(`${label} must start with ${prefix}, got ${actual || '<missing>'}`)
  }
}

function requireEquals(actual, expected, label) {
  if (actual !== expected) {
    fail(`${label} must be ${expected}, got ${actual || '<missing>'}`)
  }
}

const root = process.cwd()
const contractFile = path.join(root, 'contracts/scaffold-stack-contract.json')
const contract = JSON.parse(fs.readFileSync(contractFile, 'utf8'))
const sourceRoot = path.resolve(root, contract.sourcePath || '../infra-dev-scaffolding')
const sourceReadmeFile = path.join(sourceRoot, 'README.md')

requireEquals(contract.sourceProject, 'infra-dev-scaffolding', 'sourceProject')
requireEquals(contract.frontend.framework, 'Vue', 'frontend.framework')
requireEquals(contract.frontend.language, 'TypeScript', 'frontend.language')
requireEquals(contract.frontend.buildTool, 'Vite', 'frontend.buildTool')
requireEquals(contract.backend.framework, 'Spring Boot', 'backend.framework')
requireEquals(contract.backend.language, 'Java', 'backend.language')
requireEquals(contract.backend.javaVersion, '17', 'backend.javaVersion')

if (!fs.existsSync(sourceReadmeFile)) {
  console.log(`check-scaffold-source: source=missing path=${sourceRoot} skipped`)
  process.exit(0)
}

const sourceReadme = fs.readFileSync(sourceReadmeFile, 'utf8')
requireAnyToken('infra-dev-scaffolding/README.md', sourceReadme, [
  'Frontend: Vue 3.5 + TypeScript + Vite 7',
  '| Frontend | Vue 3.5, TypeScript, Vite 7'
])
requireAnyToken('infra-dev-scaffolding/README.md', sourceReadme, [
  'Backend: Spring Boot 3.4.5 + Java 17',
  '| Backend | Spring Boot 3.4.5, Java 17'
])
requireAnyToken('infra-dev-scaffolding/README.md', sourceReadme, [
  'Dev/Test: 后端默认 H2',
  '默认 dev profile 使用 H2',
  '| Data | H2 for dev/test'
])

const sourceFrontendPackageFile = path.join(sourceRoot, 'frontend/package.json')
const sourceBackendPomFile = path.join(sourceRoot, 'backend/pom.xml')
if (!fs.existsSync(sourceFrontendPackageFile)) {
  fail(`missing source frontend package: ${sourceFrontendPackageFile}`)
}
if (!fs.existsSync(sourceBackendPomFile)) {
  fail(`missing source backend pom: ${sourceBackendPomFile}`)
}

const sourceFrontendPackage = JSON.parse(fs.readFileSync(sourceFrontendPackageFile, 'utf8'))
const sourceBackendPom = fs.readFileSync(sourceBackendPomFile, 'utf8')

requireStartsWith(sourceFrontendPackage.dependencies?.vue, contract.frontend.frameworkVersionPrefix, 'source frontend vue')
requireStartsWith(sourceFrontendPackage.devDependencies?.vite, contract.frontend.buildToolVersionPrefix, 'source frontend vite')
requireStartsWith(sourceFrontendPackage.packageManager, contract.frontend.packageManagerPrefix, 'source frontend packageManager')
requireStartsWith(sourceFrontendPackage.dependencies?.['element-plus'], '^2.', 'source frontend element-plus')
requireToken('infra-dev-scaffolding/backend/pom.xml', sourceBackendPom, '<version>3.4.5</version>')
requireToken('infra-dev-scaffolding/backend/pom.xml', sourceBackendPom, '<java.version>17</java.version>')
requireToken('infra-dev-scaffolding/backend/pom.xml', sourceBackendPom, '<artifactId>spring-boot-starter-web</artifactId>')
requireToken('infra-dev-scaffolding/backend/pom.xml', sourceBackendPom, '<artifactId>h2</artifactId>')

console.log(
  'check-scaffold-source: ok ' +
    `frontend=${contract.frontend.framework}/${contract.frontend.language}/${contract.frontend.buildTool} ` +
    `backend=${contract.backend.framework}/${contract.backend.language}${contract.backend.javaVersion}`
)
NODE
