#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "ERROR: not inside a Git repository" >&2; exit 2; }
cd "$ROOT"

echo "============================================================"
echo " OS_00_04.GIT — REPOSITORY AUDIT"
echo "============================================================"

echo; echo "===== REPOSITORY ====="
git remote -v

echo; echo "===== CURRENT STATE ====="
echo "BRANCH=$(git branch --show-current)"
echo "HEAD=$(git rev-parse HEAD)"
git status --short

echo; echo "===== LOCAL / UPSTREAM ====="
FAILURES=0
while IFS='|' read -r branch sha upstream; do
  if [[ -z "$upstream" ]]; then
    echo "FAIL NO-UPSTREAM $branch $sha"
    FAILURES=$((FAILURES+1))
    continue
  fi
  remote_sha="$(git rev-parse "$upstream")"
  if [[ "$sha" == "$remote_sha" ]]; then
    echo "PASS $branch $sha"
  else
    counts="$(git rev-list --left-right --count "${upstream}...${branch}")"
    echo "FAIL $branch LOCAL=$sha REMOTE=$remote_sha COUNTS=$counts"
    FAILURES=$((FAILURES+1))
  fi
done < <(git for-each-ref --sort=refname --format='%(refname:short)|%(objectname)|%(upstream:short)' refs/heads/)
echo "SYNC_FAILURES=$FAILURES"

echo; echo "===== UNTRACKED NON-IGNORED ====="
git ls-files --others --exclude-standard

echo; echo "===== TRACKED GENERATED/SENSITIVE PATTERN CHECK ====="
git ls-files | grep -E '(^|/)(evidence|node_modules|dist|target|\.terraform|\.sn-ui-03-backups)(/|$)|\.tfstate($|\.)|\.tfplan$' || true

echo; echo "===== UNREACHABLE LOCAL TIPS ====="
UNREACHABLE=0
while IFS= read -r branch; do
  sha="$(git rev-parse "$branch")"
  if ! git branch -r --contains "$sha" | grep -q '[^[:space:]]'; then
    echo "FAIL $branch $sha"
    UNREACHABLE=$((UNREACHABLE+1))
  fi
done < <(git for-each-ref --format='%(refname:short)' refs/heads/)
echo "UNREACHABLE_LOCAL_TIPS=$UNREACHABLE"

echo; echo "===== TAGS ====="
git for-each-ref --sort=creatordate --format='%(refname:short)|%(objectname)|%(creatordate:iso8601)|%(subject)' refs/tags/

echo; echo "===== RESULT ====="
if (( FAILURES == 0 && UNREACHABLE == 0 )); then
  echo "OS_00_04_GIT_REPOSITORY_AUDIT=PASS"
else
  echo "OS_00_04_GIT_REPOSITORY_AUDIT=FAIL"
  exit 1
fi
