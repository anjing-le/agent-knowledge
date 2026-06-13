#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const backendSourceDir = path.join(root, 'backend/src/main/java')
const allowedFiles = new Set([
  'backend/src/main/java/com/anjing/util/DateUtils.java'
])

function fail(message) {
  console.error(`check-backend-time-contract: ${message}`)
  process.exit(1)
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'logs') {
      continue
    }

    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath, files)
    } else if (entry.name.endsWith('.java')) {
      files.push(fullPath)
    }
  }
  return files
}

if (!fs.existsSync(backendSourceDir)) {
  fail('missing backend/src/main/java')
}

const directTimePatterns = [
  /\bInstant\.now\s*\(/,
  /\bLocalDateTime\.now\s*\(/,
  /\bOffsetDateTime\.now\s*\(/,
  /\bZonedDateTime\.now\s*\(/,
  /\bnew\s+Date\s*\(/
]

for (const file of walk(backendSourceDir)) {
  const relativeFile = path.relative(root, file).replace(/\\/g, '/')
  if (allowedFiles.has(relativeFile)) {
    continue
  }

  const source = fs.readFileSync(file, 'utf8')
  for (const pattern of directTimePatterns) {
    if (pattern.test(source)) {
      fail(`${relativeFile} uses direct current time; use DateUtils instead`)
    }
  }
}

console.log('check-backend-time-contract: ok')
