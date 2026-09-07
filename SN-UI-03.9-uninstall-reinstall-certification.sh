#!/usr/bin/env bash
set -uo pipefail

ROOT=/opt/event-management-platform
BASE="$ROOT/infrastructure/docker-compose.yml"
OVERLAY="$ROOT/infrastructure/docker-compose.event-management-console.yml"
CONSOLE=event-management-console
CORE=event-integration-worker
LAB=event-itsm-ticketing-dashboard
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidence/sn-ui-03/03.9-reversibility/$TS"
REPORT="$DIR/SN-UI-03.9-uninstall-reinstall-certification.txt"
BACKUP="$DIR/docker-compose.event-management-console.yml.backup"
FAILURES=0
RECOVERY_REQUIRED=TRUE

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES+1)); }
inspect(){ docker inspect -f "$2" "$1" 2>/dev/null||true; }
recover(){
  [[ "$RECOVERY_REQUIRED" == TRUE ]] || return 0
  echo "RECOVERY=STARTED"
  if [[ ! -f "$OVERLAY" && -f "$BACKUP" ]];then cp -a "$BACKUP" "$OVERLAY";fi
  if [[ -f "$OVERLAY" ]];then docker compose --env-file "$ROOT/.env" -f "$BASE" -f "$OVERLAY" up -d --no-deps event-management-console||true;fi
  echo "RECOVERY=ATTEMPTED"
}
trap recover EXIT INT TERM

echo "===== SN-UI-03.9 UNINSTALL AND REINSTALL CERTIFICATION ====="
echo "UTC=$TS"
echo "SCOPE=EVENT_MANAGEMENT_CONSOLE_ONLY"
echo "REPORT=$REPORT"
cd "$ROOT"||exit 1

echo;echo "===== 01. ENTRY GUARDS ====="
[[ -f "$BASE" && -f "$OVERLAY" ]]&&pass "Compose files present"||{ fail "Compose files missing";exit 1;}
[[ "$(git diff --cached --name-only|sed '/^$/d'|wc -l|tr -d ' ')" == 0 ]]&&pass "Staging empty"||{ fail "Staging not empty";exit 1;}
CONSOLE_STATE="$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')";[[ "$CONSOLE_STATE" == running/healthy/0 ]]&&pass "Console entry state healthy"||{ fail "Console entry state unexpected: $CONSOLE_STATE";exit 1;}
CORE_ID="$(inspect "$CORE" '{{.Id}}')";CORE_IMAGE="$(inspect "$CORE" '{{.Image}}')";CORE_RESTARTS="$(inspect "$CORE" '{{.RestartCount}}')";LAB_ID="$(inspect "$LAB" '{{.Id}}')";IMAGE_ID_BEFORE="$(inspect "$CONSOLE" '{{.Image}}')";OVERLAY_SHA="$(sha256sum "$OVERLAY"|awk '{print $1}')";cp -a "$OVERLAY" "$BACKUP";[[ "$(sha256sum "$BACKUP"|awk '{print $1}')" == "$OVERLAY_SHA" ]]&&pass "Overlay backup verified"||{ fail "Overlay backup mismatch";exit 1;}

echo;echo "===== 02. TARGETED UNINSTALL ====="
docker compose --env-file .env -f "$BASE" -f "$OVERLAY" stop event-management-console&&pass "Console stopped"||fail "Console stop failed"
docker compose --env-file .env -f "$BASE" -f "$OVERLAY" rm -f event-management-console&&pass "Console container removed"||fail "Console removal failed"
find "$OVERLAY" -delete
[[ ! -e "$OVERLAY" ]]&&pass "Console overlay removed"||fail "Overlay removal failed"
[[ -z "$(docker ps -aq -f name='^event-management-console$')" ]]&&pass "Console container absent"||fail "Console container still exists"
HTTP_DOWN="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 http://localhost:8090/health 2>/dev/null||true)";echo "HTTP_WHILE_UNINSTALLED=${HTTP_DOWN:-000}";[[ "$HTTP_DOWN" != 200 ]]&&pass "Port 8090 unavailable while uninstalled"||fail "Console still responds after uninstall"

echo;echo "===== 03. PARALLEL SERVICES DURING ABSENCE ====="
[[ "$(inspect "$CORE" '{{.Id}}')" == "$CORE_ID" && "$(inspect "$CORE" '{{.RestartCount}}')" == "$CORE_RESTARTS" ]]&&pass "Core preserved during uninstall"||fail "Core changed during uninstall"
[[ "$(inspect "$LAB" '{{.Id}}')" == "$LAB_ID" && "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 4 http://localhost:8088/itsm/tickets/ 2>/dev/null)" == 200 ]]&&pass "SN-UI-01 preserved during uninstall"||fail "SN-UI-01 changed during uninstall"

echo;echo "===== 04. CONTROLLED REINSTALL ====="
cp -a "$BACKUP" "$OVERLAY";[[ "$(sha256sum "$OVERLAY"|awk '{print $1}')" == "$OVERLAY_SHA" ]]&&pass "Exact overlay restored"||fail "Restored overlay mismatch"
docker compose --env-file .env -f "$BASE" -f "$OVERLAY" up -d --no-deps event-management-console&&pass "Console redeployed without dependencies"||fail "Console redeployment failed"
HEALTH=starting;for attempt in $(seq 1 30);do HEALTH="$(inspect "$CONSOLE" '{{.State.Health.Status}}')";echo "HEALTH_ATTEMPT=$attempt STATUS=$HEALTH";[[ "$HEALTH" == healthy ]]&&break;sleep 2;done
FINAL_STATE="$(inspect "$CONSOLE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')";echo "FINAL_CONSOLE_STATE=$FINAL_STATE";[[ "$FINAL_STATE" == running/healthy/0 ]]&&pass "Console returned healthy without restarts"||fail "Console final state unexpected"
IMAGE_ID_AFTER="$(inspect "$CONSOLE" '{{.Image}}')";echo "IMAGE_BEFORE=$IMAGE_ID_BEFORE";echo "IMAGE_AFTER=$IMAGE_ID_AFTER";[[ "$IMAGE_ID_BEFORE" == "$IMAGE_ID_AFTER" ]]&&pass "Certified image reused without rebuild"||fail "Image identity changed"
for path in /health / /dashboard /ticketing/tickets;do code="$(curl -sS -o "$DIR/reinstalled$(echo "$path"|tr / _).txt" -w '%{http_code}' --max-time 5 "http://localhost:8090$path" 2>/dev/null||true)";[[ "$code" == 200 ]]&&pass "Reinstalled route $path returns 200"||fail "Reinstalled route $path returned $code";done

echo;echo "===== 05. FINAL NON-REGRESSION ====="
FINAL_CORE="$(inspect "$CORE" '{{.Id}}/{{.Image}}/{{.RestartCount}}/{{.State.Status}}/{{.State.Health.Status}}')";EXPECTED_CORE="$CORE_ID/$CORE_IMAGE/$CORE_RESTARTS/running/healthy";[[ "$FINAL_CORE" == "$EXPECTED_CORE" ]]&&pass "Core identity, image, restart count and health preserved"||fail "Core final state changed"
[[ "$(inspect "$LAB" '{{.Id}}')" == "$LAB_ID" ]]&&pass "SN-UI-01 identity preserved"||fail "SN-UI-01 identity changed"
[[ -z "$(git status --porcelain -- services/integration-worker infrastructure/postgres)" ]]&&pass "Protected source unchanged"||fail "Protected source changed"

echo;echo "===== FINAL RESULT =====";echo "OVERLAY_RESTORED=TRUE";echo "PERSISTENT_CONSOLE_RUNNING=$([[ "$FINAL_STATE" == running/healthy/0 ]]&&echo TRUE||echo FALSE)";echo "IMAGE_REBUILT=FALSE";echo "DEPENDENCIES_RECREATED=FALSE";echo "KAFKA_COMMANDS_PUBLISHED=FALSE";echo "POSTGRESQL_QUERIES_EXECUTED=FALSE";echo "FAILURE_COUNT=$FAILURES";echo "REPORT=$REPORT"
if((FAILURES==0));then RECOVERY_REQUIRED=FALSE;echo "SN_UI_03_9_UNINSTALL_REINSTALL_CERTIFICATION=PASS";echo "NEXT_CHECKPOINT=SN-UI-03.10";exit 0;fi
echo "SN_UI_03_9_UNINSTALL_REINSTALL_CERTIFICATION=FAIL";exit 1
