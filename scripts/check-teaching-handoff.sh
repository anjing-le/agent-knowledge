#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EXPECTED_NAME="安静"
EXPECTED_EMAIL="245548353+anjing-le@users.noreply.github.com"
BASELINE_TAG="${BASELINE_TAG:-v1-teaching-baseline}"
HANDOFF_TAG="${HANDOFF_TAG:-v1.1-teaching-handoff}"
FRONTEND_ROUTE="${FRONTEND_ROUTE:-http://localhost:20001/#/kb/pipeline}"

fail() {
  echo "check-teaching-handoff: $*" >&2
  exit 1
}

warn() {
  echo "warn: $*"
}

ok() {
  echo "ok: $*"
}

section() {
  printf '\n== %s ==\n' "$1"
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: $file"
}

verify_tag() {
  local tag="$1"
  git rev-parse -q --verify "refs/tags/$tag" >/dev/null \
    || fail "missing local tag: $tag"

  local tag_commit
  tag_commit="$(git rev-list -n 1 "$tag")"
  echo "$tag -> $tag_commit"

  local remote_tag
  local tag_status
  set +e
  remote_tag="$(git ls-remote --tags origin "$tag" 2>/dev/null)"
  tag_status=$?
  set -e
  if (( tag_status != 0 )); then
    warn "cannot query origin tag: $tag"
  elif [[ -z "$remote_tag" ]]; then
    fail "missing remote tag: $tag"
  else
    echo "$remote_tag"
    ok "$tag is available locally and remotely"
  fi
}

latest_evidence_dir() {
  if [[ -n "${EVIDENCE_DATE:-}" ]]; then
    printf 'docs/evidence/%s\n' "$EVIDENCE_DATE"
    return
  fi

  find docs/evidence -mindepth 1 -maxdepth 1 -type d -name '????-??-??' | sort | tail -n 1
}

section "Git Identity"
git_name="$(git config --get user.name || true)"
git_email="$(git config --get user.email || true)"
head_sha="$(git rev-parse HEAD)"
head_line="$(git log -1 --pretty=format:'%h %an <%ae> %s')"
branch_line="$(git status --short --branch | sed -n '1p')"

echo "$branch_line"
echo "$head_line"
echo "user.name=$git_name"
echo "user.email=$git_email"

[[ "$git_name" == "$EXPECTED_NAME" ]] || fail "git user.name must be $EXPECTED_NAME"
[[ "$git_email" == "$EXPECTED_EMAIL" ]] || fail "git user.email must be $EXPECTED_EMAIL"
git log -1 --pretty=format:'%ae' | rg -q --fixed-strings "$EXPECTED_EMAIL" \
  || fail "latest commit author email must be $EXPECTED_EMAIL"
ok "commit identity belongs to anjing-le"

section "Worktree"
unexpected_status=()
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  case "$line" in
    '?? reference/'|'?? reference/'*) echo "known untracked: $line" ;;
    *) unexpected_status+=("$line") ;;
  esac
done < <(git status --porcelain=v1)

if (( ${#unexpected_status[@]} > 0 )); then
  printf '%s\n' "${unexpected_status[@]}" >&2
  if [[ "${ALLOW_DIRTY:-false}" == "true" ]]; then
    warn "worktree has unexpected local changes; continuing because ALLOW_DIRTY=true"
  else
    fail "worktree has unexpected local changes"
  fi
else
  ok "no unexpected local changes"
fi

section "Remote Heads"
set +e
remote_heads="$(git ls-remote --heads origin main master 2>/dev/null)"
remote_status=$?
set -e

if (( remote_status != 0 )); then
  warn "cannot query origin heads; skip remote pointer check"
else
  echo "$remote_heads"
  main_sha="$(printf '%s\n' "$remote_heads" | awk '$2 == "refs/heads/main" { print $1 }')"
  master_sha="$(printf '%s\n' "$remote_heads" | awk '$2 == "refs/heads/master" { print $1 }')"
  [[ -n "$main_sha" ]] || fail "origin/main is missing"
  [[ "$main_sha" == "$head_sha" ]] || fail "origin/main does not point to HEAD"
  if [[ -n "$master_sha" ]]; then
    [[ "$master_sha" == "$head_sha" ]] || fail "origin/master does not point to HEAD"
  fi
  ok "origin/main and origin/master are aligned with HEAD"
fi

section "Quality Gate"
if command -v gh >/dev/null 2>&1; then
  set +e
  runs_json="$(gh run list --limit 8 --json headSha,status,conclusion,workflowName,url,createdAt 2>/dev/null)"
  gh_status=$?
  set -e

  if (( gh_status != 0 )) || [[ -z "$runs_json" ]]; then
    warn "cannot read GitHub Actions via gh; run gh auth status if needed"
  else
    set +e
    ci_summary="$(
      printf '%s' "$runs_json" | HEAD_SHA="$head_sha" node -e '
const fs = require("fs")
const runs = JSON.parse(fs.readFileSync(0, "utf8"))
const headSha = process.env.HEAD_SHA
const qualityRuns = runs.filter((run) => run.headSha === headSha && run.workflowName === "Quality Gate")

for (const run of qualityRuns) {
  console.log(`${run.workflowName}: ${run.status}/${run.conclusion || "pending"} ${run.url}`)
}

if (qualityRuns.length === 0) {
  console.log("no Quality Gate run found for HEAD")
  process.exit(2)
}

const failingRuns = qualityRuns.filter((run) => run.status !== "completed" || run.conclusion !== "success")
if (failingRuns.length > 0) {
  process.exit(1)
}
'
    )"
    ci_status=$?
    set -e
    echo "$ci_summary"
    case "$ci_status" in
      0) ok "Quality Gate is green for HEAD" ;;
      2) warn "no Quality Gate run found for current HEAD" ;;
      *) fail "Quality Gate is not green for current HEAD" ;;
    esac
  fi
else
  warn "gh is not installed; skip GitHub Actions check"
fi

section "Evidence Package"
evidence_dir="$(latest_evidence_dir)"
[[ -n "$evidence_dir" ]] || fail "no dated evidence package found under docs/evidence"
require_file "$evidence_dir/README.md"
require_file "$evidence_dir/runtime/summary.txt"
require_file "$evidence_dir/runtime/rag-citation-evidence.md"
require_file "$evidence_dir/runtime/retrieval-adapter-status.json"

screenshot_count="$(find "$evidence_dir/screenshots" -type f -name '*.png' 2>/dev/null | wc -l | tr -d ' ')"
[[ "$screenshot_count" -ge 1 ]] || fail "evidence package must include at least one screenshot"
echo "evidence=$evidence_dir"
echo "screenshots=$screenshot_count"
ok "evidence package is ready"

section "Release Tags"
verify_tag "$BASELINE_TAG"
verify_tag "$HANDOFF_TAG"

section "Classroom Commands"
cat <<EOF
# Full gate
./scripts/quality-gate.sh

# Evidence refresh
./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force --include-doc-parser-live

# Local services
(cd doc-parser && python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001)
(cd backend && mvn spring-boot:run)
(cd frontend && pnpm install && pnpm dev)

# Demo loop
./scripts/seed-rag-demo.sh
./scripts/evaluate-rag-retrieval.sh
./scripts/probe-rag-demo-runtime.sh
./scripts/probe-rag-ingestion-runtime.sh

# Teaching route
$FRONTEND_ROUTE

# Release tags
git show $BASELINE_TAG --stat
git show $HANDOFF_TAG --stat
EOF

section "Result"
ok "V1.1 teaching handoff is ready"
