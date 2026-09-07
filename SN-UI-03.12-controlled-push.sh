#!/usr/bin/env bash
set -euo pipefail

ROOT=/opt/event-management-platform
BRANCH=feature/os-01-02-servicenow-core-foundation
EXPECTED_HEAD=85af2ff1d7d37c2b656e72bef99a1a512deb0589
EXPECTED_SUBJECT="feat(console): add event management console foundation"
REMOTE=origin
CONSOLE=event-management-console
CORE=event-integration-worker
LAB=event-itsm-ticketing-dashboard
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidence/sn-ui-03/03.12-push/$TS"
REPORT="$DIR/SN-UI-03.12-controlled-push.txt"

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*";exit 1; }
inspect(){ docker inspect -f "$2" "$1" 2>/dev/null||true; }

echo "===== SN-UI-03.12 CONTROLLED PUSH ====="
echo "UTC=$TS"
echo "REMOTE=$REMOTE"
echo "BRANCH=$BRANCH"
echo "EXPECTED_HEAD=$EXPECTED_HEAD"
echo "FORCE_PUSH=FALSE"
echo "TAGS_PUSHED=FALSE"
echo "REPORT=$REPORT"
cd "$ROOT"

echo;echo "===== 01. LOCAL RELEASE GUARDS ====="
[[ "$(git branch --show-current)" == "$BRANCH" ]]&&pass "Correct branch"||fail "Unexpected branch"
[[ "$(git rev-parse HEAD)" == "$EXPECTED_HEAD" ]]&&pass "Certified commit is HEAD"||fail "Unexpected HEAD"
[[ "$(git log -1 --pretty=%s)" == "$EXPECTED_SUBJECT" ]]&&pass "Certified commit subject correct"||fail "Unexpected commit subject"
git diff --cached --quiet&&pass "Staging empty"||fail "Staging not empty"
git diff --quiet&&pass "Tracked worktree clean"||fail "Tracked worktree contains changes"
git remote get-url "$REMOTE" >/dev/null 2>&1&&pass "Origin remote configured"||fail "Origin remote unavailable"
[[ -z "$(git status --porcelain -- services/integration-worker infrastructure/postgres)" ]]&&pass "Protected source unchanged"||fail "Protected source changed"

CONSOLE_STATE="$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')"
CORE_STATE="$(inspect "$CORE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')"
CORE_ID="$(inspect "$CORE" '{{.Id}}')";LAB_ID="$(inspect "$LAB" '{{.Id}}')"
echo "CONSOLE_STATE=$CONSOLE_STATE";echo "CORE_STATE=$CORE_STATE"
[[ "$CONSOLE_STATE" == running/healthy/0 ]]&&pass "Console runtime certified"||fail "Console runtime unexpected"
[[ "$CORE_STATE" == running/healthy/0 ]]&&pass "ServiceNow Core certified"||fail "ServiceNow Core runtime unexpected"

echo;echo "===== 02. REMOTE PRE-PUSH STATE ====="
REMOTE_BEFORE="$(git ls-remote --heads "$REMOTE" "refs/heads/$BRANCH"|awk 'NR==1{print $1}')"
echo "REMOTE_HEAD_BEFORE=${REMOTE_BEFORE:-ABSENT}"
if [[ -n "$REMOTE_BEFORE" ]];then
  if git merge-base --is-ancestor "$REMOTE_BEFORE" "$EXPECTED_HEAD" 2>/dev/null;then pass "Push is fast-forward from known remote head";else echo "INFO=Remote object may not exist locally; normal push will enforce non-force safety";fi
else
  pass "Remote branch absent; normal push may create it"
fi

echo;echo "===== 03. PUSH ====="
git push "$REMOTE" "HEAD:refs/heads/$BRANCH"
pass "Normal push command completed"

echo;echo "===== 04. REMOTE VERIFICATION ====="
REMOTE_AFTER="$(git ls-remote --heads "$REMOTE" "refs/heads/$BRANCH"|awk 'NR==1{print $1}')"
echo "REMOTE_HEAD_AFTER=${REMOTE_AFTER:-ABSENT}"
[[ "$REMOTE_AFTER" == "$EXPECTED_HEAD" ]]&&pass "Remote branch matches certified commit"||fail "Remote SHA mismatch"

echo;echo "===== 05. POST-PUSH SAFETY ====="
[[ "$(git rev-parse HEAD)" == "$EXPECTED_HEAD" ]]&&pass "Local HEAD unchanged"||fail "Local HEAD changed"
git diff --cached --quiet&&pass "Staging remains empty"||fail "Staging changed"
[[ "$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')" == running/healthy/0 ]]&&pass "Console remains healthy"||fail "Console state changed"
[[ "$(inspect "$CORE" '{{.Id}}')" == "$CORE_ID" && "$(inspect "$CORE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')" == running/healthy/0 ]]&&pass "Core identity and state preserved"||fail "Core changed"
[[ "$(inspect "$LAB" '{{.Id}}')" == "$LAB_ID" ]]&&pass "SN-UI-01 identity preserved"||fail "SN-UI-01 changed"

echo;echo "===== FINAL RESULT ====="
echo "SN_UI_03_12_CONTROLLED_PUSH=PASS"
echo "SN_UI_03_CONSOLE_FOUNDATION_V1_0_0=PUSHED"
echo "LOCAL_HEAD=$EXPECTED_HEAD"
echo "REMOTE_HEAD=$REMOTE_AFTER"
echo "REMOTE_BRANCH=$BRANCH"
echo "FORCE_PUSH_EXECUTED=FALSE"
echo "TAGS_PUSHED=FALSE"
echo "RUNTIME_MUTATED=FALSE"
echo "REPORT=$REPORT"
