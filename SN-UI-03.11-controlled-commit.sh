#!/usr/bin/env bash
set -euo pipefail

ROOT=/opt/event-management-platform
EXPECTED_BRANCH=feature/os-01-02-servicenow-core-foundation
EXPECTED_HEAD=b2475908289a427c6c1c9d728ee93d1d4ffa1c0c
COMMIT_SUBJECT="feat(console): add event management console foundation"
CONSOLE=event-management-console
CORE=event-integration-worker
LAB=event-itsm-ticketing-dashboard
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidence/sn-ui-03/03.11-commit/$TS"
REPORT="$DIR/SN-UI-03.11-controlled-commit.txt"
INTENDED="$DIR/intended-files.txt"
STAGED="$DIR/staged-files.txt"
COMMIT_COMPLETE=FALSE

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1

cleanup_staging(){
  [[ "$COMMIT_COMPLETE" == TRUE ]] && return 0
  [[ -f "$INTENDED" ]] || return 0
  echo "CLEANUP=UNSTAGING_INTENDED_FILES"
  while IFS= read -r file;do git restore --staged -- "$file" >/dev/null 2>&1||true;done <"$INTENDED"
}
trap cleanup_staging EXIT INT TERM
fail(){ echo "ASSERTION=FAIL | $*";exit 1; }
pass(){ echo "ASSERTION=PASS | $*"; }
inspect(){ docker inspect -f "$2" "$1" 2>/dev/null||true; }

echo "===== SN-UI-03.11 CONTROLLED COMMIT ====="
echo "UTC=$TS"
echo "EXPECTED_BRANCH=$EXPECTED_BRANCH"
echo "EXPECTED_HEAD=$EXPECTED_HEAD"
echo "COMMIT_SUBJECT=$COMMIT_SUBJECT"
echo "PUSH_AUTHORIZED=FALSE"
echo "REPORT=$REPORT"
cd "$ROOT"

echo;echo "===== 01. ENTRY GUARDS ====="
[[ "$(git branch --show-current)" == "$EXPECTED_BRANCH" ]]&&pass "Correct branch"||fail "Unexpected branch"
[[ "$(git rev-parse HEAD)" == "$EXPECTED_HEAD" ]]&&pass "Correct certified baseline"||fail "Unexpected HEAD"
git diff --cached --quiet&&pass "Staging empty"||fail "Staging not empty"
[[ -z "$(git status --porcelain -- services/integration-worker infrastructure/postgres)" ]]&&pass "Protected source unchanged"||fail "Protected source changed"
[[ -f services/event-management-console/package.json && -f services/event-management-console/Dockerfile ]]&&pass "Console source present"||fail "Console source incomplete"
[[ -f infrastructure/docker-compose.event-management-console.yml ]]&&pass "Console overlay present"||fail "Console overlay missing"
CONSOLE_STATE="$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')";CORE_STATE="$(inspect "$CORE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')";LAB_ID="$(inspect "$LAB" '{{.Id}}')";CORE_ID="$(inspect "$CORE" '{{.Id}}')";echo "CONSOLE_STATE=$CONSOLE_STATE";echo "CORE_STATE=$CORE_STATE";[[ "$CONSOLE_STATE" == running/healthy/0 ]]&&pass "Console certified runtime active"||fail "Console runtime unexpected";[[ "$CORE_STATE" == running/healthy/0 ]]&&pass "Core certified runtime active"||fail "Core runtime unexpected"

echo;echo "===== 02. CANONICAL FILE LIST ====="
find services/event-management-console -type f \
  ! -path '*/node_modules/*' ! -path '*/dist/*' ! -path '*/.sn-ui-03-backups/*' \
  ! -name '.sn-ui-*' ! -name '*.tsbuildinfo' ! -name 'vite.config.js' ! -name 'vite.config.d.ts' \
  -print | sort >"$INTENDED"
echo infrastructure/docker-compose.event-management-console.yml >>"$INTENDED"
sort -u -o "$INTENDED" "$INTENDED"
FILE_COUNT="$(wc -l <"$INTENDED"|tr -d ' ')";echo "INTENDED_FILE_COUNT=$FILE_COUNT";cat "$INTENDED"
[[ "$FILE_COUNT" -ge 15 ]]&&pass "Canonical file list populated"||fail "Unexpectedly small file list"
if grep -E '(^|/)(node_modules|dist|evidence|\.sn-ui-03-backups)(/|$)|/\.sn-ui-|\.tsbuildinfo$' "$INTENDED";then fail "Excluded artifacts entered intended list";else pass "Generated artifacts and LAB markers excluded";fi

echo;echo "===== 03. SELECTIVE STAGING ====="
while IFS= read -r file;do git add -- "$file";done <"$INTENDED"
git diff --cached --name-only | sort >"$STAGED"
echo "----- STAGED FILES -----";cat "$STAGED"
if diff -u "$INTENDED" "$STAGED" >"$DIR/file-list.diff";then pass "Staged scope exactly matches intended scope";else cat "$DIR/file-list.diff";fail "Staged scope mismatch";fi
if grep -E '^(services/integration-worker|infrastructure/postgres|evidence/|services/itsm-ticketing-dashboard|infrastructure/docker-compose\.itsm-dashboard\.yml)' "$STAGED";then fail "Forbidden path staged";else pass "Protected and auxiliary paths excluded";fi
git diff --cached --check&&pass "Staged diff passes whitespace validation"||fail "Staged diff validation failed"
git diff --cached --stat | tee "$DIR/staged-stat.txt"

echo;echo "===== 04. COMMIT ====="
git commit -m "$COMMIT_SUBJECT"
COMMIT_COMPLETE=TRUE
NEW_HEAD="$(git rev-parse HEAD)";NEW_SUBJECT="$(git log -1 --pretty=%s)";echo "NEW_HEAD=$NEW_HEAD";echo "NEW_SUBJECT=$NEW_SUBJECT";[[ "$NEW_HEAD" != "$EXPECTED_HEAD" ]]&&pass "New commit created"||fail "HEAD did not advance";[[ "$NEW_SUBJECT" == "$COMMIT_SUBJECT" ]]&&pass "Commit subject correct"||fail "Commit subject mismatch";git diff --cached --quiet&&pass "Staging empty after commit"||fail "Staging not empty after commit"

echo;echo "===== 05. POST-COMMIT RUNTIME SAFETY ====="
[[ "$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')" == running/healthy/0 ]]&&pass "Console remains healthy"||fail "Console state changed"
[[ "$(inspect "$CORE" '{{.Id}}')" == "$CORE_ID" && "$(inspect "$CORE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')" == running/healthy/0 ]]&&pass "Core identity and state preserved"||fail "Core changed"
[[ "$(inspect "$LAB" '{{.Id}}')" == "$LAB_ID" ]]&&pass "SN-UI-01 identity preserved"||fail "SN-UI-01 changed"
curl -fsS --max-time 5 http://localhost:8090/health | tee "$DIR/console-health.json" >/dev/null&&pass "Console health endpoint remains available"||fail "Console health endpoint unavailable"

echo;echo "===== FINAL RESULT =====";echo "SN_UI_03_11_CONTROLLED_COMMIT=PASS";echo "SN_UI_03_CONSOLE_FOUNDATION_V1_0_0=CERTIFIED";echo "COMMIT=$NEW_HEAD";echo "BRANCH=$EXPECTED_BRANCH";echo "PUSH_EXECUTED=FALSE";echo "EVIDENCE_PRESERVED=TRUE";echo "REPORT=$REPORT";echo "NEXT_ACTION=REVIEW_COMMIT_AND_AUTHORIZE_PUSH_IF_DESIRED"
