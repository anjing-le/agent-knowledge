#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "create-demo-evidence: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/create-demo-evidence.sh [--date YYYY-MM-DD] [--dry-run] [--force]

Creates a dated demo evidence package under docs/evidence/YYYY-MM-DD/.

Options:
  --date YYYY-MM-DD  Evidence package date. Defaults to today.
  --dry-run          Validate inputs and print the target without writing files.
  --force            Allow replacing README.md in an existing package.
EOF
}

DATE_VALUE=""
DRY_RUN=false
FORCE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --date)
      [[ $# -ge 2 ]] || fail "--date requires YYYY-MM-DD"
      DATE_VALUE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -z "$DATE_VALUE" ]]; then
        DATE_VALUE="$1"
        shift
      else
        fail "unexpected argument: $1"
      fi
      ;;
  esac
done

DATE_VALUE="${DATE_VALUE:-$(date +%F)}"
[[ "$DATE_VALUE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail "date must match YYYY-MM-DD: $DATE_VALUE"

TEMPLATE="docs/evidence/TEMPLATE.md"
TARGET_DIR="docs/evidence/$DATE_VALUE"
TARGET_README="$TARGET_DIR/README.md"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || true)"
COMMIT="${COMMIT:-unknown}"

[[ -f "$TEMPLATE" ]] || fail "missing template: $TEMPLATE"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "create-demo-evidence: dry-run"
  echo "create-demo-evidence: target=$TARGET_DIR"
  echo "create-demo-evidence: template=$TEMPLATE"
  echo "create-demo-evidence: commit=$COMMIT"
  exit 0
fi

if [[ -f "$TARGET_README" && "$FORCE" != "true" ]]; then
  fail "$TARGET_README already exists; pass --force to replace it intentionally"
fi

mkdir -p "$TARGET_DIR/screenshots" "$TARGET_DIR/outputs" "$TARGET_DIR/runtime"

node - "$TEMPLATE" "$TARGET_README" "$DATE_VALUE" "$COMMIT" <<'NODE'
const fs = require('fs')

const [, , templateFile, targetFile, dateValue, commit] = process.argv
const template = fs.readFileSync(templateFile, 'utf8')
const rendered = template
  .replace('Evidence YYYY-MM-DD', `Evidence ${dateValue}`)
  .replace('`<commit>`', `\`${commit}\``)

fs.writeFileSync(targetFile, rendered)
NODE

: > "$TARGET_DIR/screenshots/.gitkeep"
: > "$TARGET_DIR/outputs/.gitkeep"
: > "$TARGET_DIR/runtime/.gitkeep"

echo "create-demo-evidence: ok"
echo "create-demo-evidence: target=$TARGET_DIR"

