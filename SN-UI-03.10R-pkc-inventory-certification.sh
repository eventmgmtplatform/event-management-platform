#!/usr/bin/env bash
set -uo pipefail

ROOT=/opt/event-management-platform
ZIP="${1:-$HOME/Downloads/EventManagement_SN-UI-03_Console_Foundation_Package_v1.0.0.zip}"
EXPECTED_SHA="6d6b2f7707ba4ecde75e7a3b684059f4332045216cec86467dbf134429e4cb41"
PREFIX="EventManagement_SN-UI-03_Console_Foundation_Package_v1.0.0"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidence/sn-ui-03/03.10R-pkc-inventory/$TS"
REPORT="$DIR/SN-UI-03.10R-pkc-inventory-certification.txt"
INVENTORY="$DIR/zip-inventory.txt"
FAILURES=0

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES+1)); }
require_entry(){ local entry="$1" label="$2";grep -Fxq "$entry" "$INVENTORY"&&pass "$label"||fail "$label missing: $entry"; }

echo "===== SN-UI-03.10R PKC INVENTORY CERTIFICATION ====="
echo "UTC=$TS"
echo "REMEDIATION=AVOID_UNZIP_GREP_SIGPIPE_FALSE_FAILURE"
echo "ZIP=$ZIP"
echo "REPORT=$REPORT"

[[ -f "$ZIP" ]]&&pass "Final PKC ZIP found"||{ fail "Final PKC ZIP missing";exit 1;}
ACTUAL_SHA="$(sha256sum "$ZIP"|awk '{print $1}')"
echo "EXPECTED_SHA256=$EXPECTED_SHA"
echo "ACTUAL_SHA256=$ACTUAL_SHA"
[[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]]&&pass "Canonical PKC checksum matches"||fail "PKC checksum mismatch"
unzip -t "$ZIP" >"$DIR/unzip-test.txt"&&pass "ZIP integrity valid"||fail "ZIP integrity failed"
unzip -Z1 "$ZIP" >"$INVENTORY"&&pass "Stable ZIP inventory generated"||fail "ZIP inventory failed"

echo
echo "===== REQUIRED DOCUMENTATION ====="
for file in knowledge-base.json architecture.md operational-runbook.md decisions-adr.md timeline.json;do require_entry "$PREFIX/docs/$file" "PKC artifact found: $file";done
require_entry "$PREFIX/docs/EventManagement_SN-UI-03_Console_Foundation_Architecture_v1.0.0.drawio" "Editable Draw.io architecture found"
require_entry "$PREFIX/MANIFEST.md" "Manifest found"
require_entry "$PREFIX/README.md" "Package README found"

echo
echo "===== CANONICAL SOURCE ====="
require_entry "$PREFIX/source/services/event-management-console/package.json" "Console package source found"
require_entry "$PREFIX/source/services/event-management-console/Dockerfile" "Console Dockerfile found"
require_entry "$PREFIX/source/services/event-management-console/nginx.conf" "Nginx runtime configuration found"
require_entry "$PREFIX/source/services/event-management-console/src/app/App.tsx" "Console application source found"
require_entry "$PREFIX/source/services/event-management-console/src/modules/ticketing/pages/TicketDashboardPage.tsx" "Ticketing dashboard source found"
require_entry "$PREFIX/source/infrastructure/docker-compose.event-management-console.yml" "Compose overlay found"

echo
echo "===== OPERATIONAL SCRIPTS ====="
SCRIPT_COUNT="$(grep -Ec "^$PREFIX/scripts/.+\.sh$" "$INVENTORY"||true)"
echo "SCRIPT_COUNT=$SCRIPT_COUNT"
[[ "$SCRIPT_COUNT" -ge 7 ]]&&pass "Checkpoint scripts included"||fail "Expected checkpoint scripts missing"

echo
echo "===== LIVE SAFETY ====="
CONSOLE="$(docker inspect -f '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}' event-management-console 2>/dev/null||true)"
CORE="$(docker inspect -f '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}' event-integration-worker 2>/dev/null||true)"
echo "CONSOLE_STATE=$CONSOLE"
echo "CORE_STATE=$CORE"
[[ "$CONSOLE" == running/healthy/0 ]]&&pass "Live Console remains certified"||fail "Live Console state unexpected"
[[ "$CORE" == running/healthy/0 ]]&&pass "ServiceNow Core remains protected"||fail "ServiceNow Core state unexpected"
[[ "$(git diff --cached --name-only|sed '/^$/d'|wc -l|tr -d ' ')" == 0 ]]&&pass "Staging remains empty"||fail "Staging not empty"

echo
echo "===== FINAL RESULT ====="
echo "PKC_MUTATED=FALSE"
echo "RUNTIME_MUTATED=FALSE"
echo "FAILURE_COUNT=$FAILURES"
echo "REPORT=$REPORT"
if((FAILURES==0));then echo "SN_UI_03_10R_PKC_INVENTORY_CERTIFICATION=PASS";echo "SN_UI_03_10_PKC=CERTIFIED";echo "NEXT_CHECKPOINT=SN-UI-03.11_PENDING_AUTHORIZATION";exit 0;fi
echo "SN_UI_03_10R_PKC_INVENTORY_CERTIFICATION=FAIL";exit 1
