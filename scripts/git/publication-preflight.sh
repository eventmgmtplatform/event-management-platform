#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "ERROR: not inside a Git repository" >&2; exit 2; }
cd "$ROOT"

BRANCH="$(git branch --show-current)"
HEAD="$(git rev-parse HEAD)"

echo "============================================================"
echo " OS_00_04.GIT — PUBLICATION PREFLIGHT"
echo "============================================================"
echo "BRANCH=$BRANCH"
echo "HEAD=$HEAD"

echo; echo "===== WORKTREE ====="
git status --short

echo; echo "===== STAGED ====="
git diff --cached --name-status

echo; echo "===== UNSTAGED TRACKED ====="
git diff --name-status

echo; echo "===== UNTRACKED NON-IGNORED ====="
git ls-files --others --exclude-standard

echo; echo "===== STAGED GENERATED/EVIDENCE CHECK ====="
FORBIDDEN="$(git diff --cached --name-only | grep -E '(^|/)evidence/|(^|/)node_modules/|(^|/)target/|(^|/)\.terraform/|\.tfstate($|\.)|\.tfplan$|\.sn-ui-03-backups/|/\.sn-ui-03\.[^/]+$' || true)"
if [[ -n "$FORBIDDEN" ]]; then
  echo "$FORBIDDEN"
  echo "FAIL: forbidden generated/state/evidence material staged"
else
  echo "PASS: no forbidden generated/state/evidence paths staged"
fi

echo; echo "===== SECRET-LIKE ADDED LINES ====="
git diff --cached -U0 | grep '^+' | grep -v '^+++' | grep -Ei 'password|passwd|secret|token|credential|api[_-]?key|private[_-]?key|authorization|bearer|basic' || true

echo; echo "===== LARGE STAGED FILES (>1 MiB) ====="
LARGE=0
while IFS= read -r path; do
  [[ -n "$path" ]] || continue
  size="$(git cat-file -s ":$path" 2>/dev/null || echo 0)"
  if (( size > 1048576 )); then
    echo "$size $path"
    LARGE=$((LARGE+1))
  fi
done < <(git diff --cached --name-only --diff-filter=ACMR)
(( LARGE == 0 )) && echo "PASS: no staged file exceeds 1 MiB"

echo; echo "===== WHITESPACE CHECK ====="
git diff --cached --check || true

echo; echo "===== UPSTREAM ====="
UPSTREAM="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || true)"
if [[ -n "$UPSTREAM" ]]; then
  echo "UPSTREAM=$UPSTREAM"
  echo "LOCAL=$(git rev-parse HEAD)"
  echo "REMOTE=$(git rev-parse "$UPSTREAM")"
  echo "COUNTS=$(git rev-list --left-right --count "${UPSTREAM}...HEAD")"
else
  echo "UPSTREAM=ABSENT (acceptable for a new unpublished branch; verify remote absence before push)"
fi

echo; echo "===== RESULT ====="
if [[ -n "$FORBIDDEN" ]]; then
  echo "OS_00_04_GIT_PUBLICATION_PREFLIGHT=FAIL"
  exit 1
fi
if (( LARGE > 0 )); then
  echo "OS_00_04_GIT_PUBLICATION_PREFLIGHT=FAIL"
  exit 1
fi
echo "OS_00_04_GIT_PUBLICATION_PREFLIGHT=PASS_WITH_MANUAL_SECRET_REVIEW_REQUIRED"
