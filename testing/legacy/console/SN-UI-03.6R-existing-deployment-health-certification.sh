#!/usr/bin/env bash
set -uo pipefail

ROOT=/opt/event-management-platform
CONSOLE=event-management-console
CORE=event-integration-worker
LAB=event-itsm-ticketing-dashboard
EXPECTED_IMAGE="event-management/event-management-console:1.0.0"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidences/sn-ui-03/03.6R-health-certification/$TS"
REPORT="$DIR/SN-UI-03.6R-existing-deployment-health-certification.txt"
FAILURES=0

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES+1)); }
inspect(){ docker inspect -f "$2" "$1" 2>/dev/null || true; }

echo "===== SN-UI-03.6R EXISTING DEPLOYMENT HEALTH CERTIFICATION ====="
echo "UTC=$TS"
echo "MODE=READ_ONLY_EXISTING_DEPLOYMENT_VALIDATION"
echo "BUILD_EXECUTED=FALSE"
echo "COMPOSE_UP_EXECUTED=FALSE"
echo "REPORT=$REPORT"

cd "$ROOT" || exit 1

echo
echo "===== 01. DEPLOYMENT IDENTITY ====="
STATUS="$(inspect "$CONSOLE" '{{.State.Status}}')"
IMAGE_NAME="$(inspect "$CONSOLE" '{{.Config.Image}}')"
IMAGE_ID="$(inspect "$CONSOLE" '{{.Image}}')"
RESTARTS="$(inspect "$CONSOLE" '{{.RestartCount}}')"
STARTED_AT="$(inspect "$CONSOLE" '{{.State.StartedAt}}')"
echo "CONSOLE_STATUS=${STATUS:-ABSENT}"
echo "CONSOLE_IMAGE_NAME=${IMAGE_NAME:-ABSENT}"
echo "CONSOLE_IMAGE_ID=${IMAGE_ID:-ABSENT}"
echo "CONSOLE_RESTARTS=${RESTARTS:-ABSENT}"
echo "CONSOLE_STARTED_AT=${STARTED_AT:-ABSENT}"
[[ "$STATUS" == running ]] && pass "Console is running" || fail "Console is not running"
[[ "$IMAGE_NAME" == "$EXPECTED_IMAGE" ]] && pass "Console image name correct" || fail "Unexpected Console image"
[[ "$RESTARTS" == 0 ]] && pass "Console restart count is zero" || fail "Console has restarted"

echo
echo "===== 02. DOCKER HEALTH CONVERGENCE ====="
HEALTH="$(inspect "$CONSOLE" '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}')"
for attempt in $(seq 1 30); do
  echo "HEALTH_ATTEMPT=$attempt STATUS=$HEALTH"
  [[ "$HEALTH" == healthy ]] && break
  [[ "$HEALTH" == unhealthy ]] && break
  sleep 2
  HEALTH="$(inspect "$CONSOLE" '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}')"
done
echo "CONSOLE_HEALTH=$HEALTH"
[[ "$HEALTH" == healthy ]] && pass "Console Docker health converged to healthy" || fail "Console Docker health did not become healthy"

echo
echo "===== 03. HTTP AND SPA ROUTES ====="
for path in /health / /dashboard /ticketing/tickets; do
  safe="$(echo "$path" | tr / _)"
  code="$(curl -sS -o "$DIR/response${safe}.txt" -w '%{http_code}' --max-time 5 "http://localhost:8090$path" 2>/dev/null || true)"
  echo "HTTP_PATH=$path CODE=${code:-000}"
  [[ "$code" == 200 ]] && pass "Route $path returns 200" || fail "Route $path returned ${code:-000}"
done
grep -Fq 'event-management-console' "$DIR/response_health.txt" 2>/dev/null && pass "Health component identity correct" || fail "Health component identity missing"

echo
echo "===== 04. PARALLEL SERVICE PRESERVATION ====="
LAB_STATUS="$(inspect "$LAB" '{{.State.Status}}')"
LAB_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8088/itsm/tickets/ 2>/dev/null || true)"
echo "LAB_STATUS=${LAB_STATUS:-ABSENT}"
echo "LAB_HTTP=${LAB_HTTP:-000}"
[[ "$LAB_STATUS" == running && "$LAB_HTTP" == 200 ]] && pass "SN-UI-01 remains available" || fail "SN-UI-01 state unexpected"

CORE_STATUS="$(inspect "$CORE" '{{.State.Status}}')"
CORE_HEALTH="$(inspect "$CORE" '{{if .State.Health}}{{.State.Health.Status}}{{end}}')"
CORE_RESTARTS="$(inspect "$CORE" '{{.RestartCount}}')"
echo "CORE_STATUS=${CORE_STATUS:-ABSENT}"
echo "CORE_HEALTH=${CORE_HEALTH:-ABSENT}"
echo "CORE_RESTARTS=${CORE_RESTARTS:-ABSENT}"
[[ "$CORE_STATUS" == running && "$CORE_HEALTH" == healthy && "$CORE_RESTARTS" == 0 ]] && pass "ServiceNow Core remains healthy and unrestarted" || fail "ServiceNow Core state unexpected"
[[ -z "$(git status --porcelain -- services/integration-worker infrastructure/postgres)" ]] && pass "Protected source unchanged" || fail "Protected source changed"

echo
echo "===== 05. ORPHAN WARNING ASSESSMENT ====="
echo "ORPHAN_WARNING_COMPONENT=event-itsm-ticketing-dashboard"
echo "REMOVE_ORPHANS_EXECUTED=FALSE"
echo "ORPHAN_WARNING_IMPACT=NONE"
[[ "$LAB_STATUS" == running ]] && pass "Auxiliary lab preserved despite Compose orphan warning" || fail "Auxiliary lab was not preserved"

echo
echo "===== FINAL RESULT ====="
echo "PERSISTENT_CONSOLE_RUNNING=$([[ "$STATUS" == running ]] && echo TRUE || echo FALSE)"
echo "CONSOLE_URL=http://localhost:8090/"
echo "TICKETING_URL=http://localhost:8090/ticketing/tickets"
echo "LAB_URL=http://localhost:8088/itsm/tickets/"
echo "INTEGRATION_WORKER_MODIFIED=FALSE"
echo "INTEGRATION_WORKER_REBUILT=FALSE"
echo "INTEGRATION_WORKER_RESTARTED=FALSE"
echo "POSTGRESQL_MIGRATIONS_EXECUTED=FALSE"
echo "KAFKA_COMMANDS_PUBLISHED=FALSE"
echo "FAILURE_COUNT=$FAILURES"
echo "REPORT=$REPORT"
if ((FAILURES==0)); then
  echo "SN_UI_03_6R_HEALTH_CERTIFICATION=PASS"
  echo "SN_UI_03_6_PARALLEL_DEPLOYMENT=CERTIFIED"
  echo "NEXT_CHECKPOINT=SN-UI-03.7"
  exit 0
fi
echo "SN_UI_03_6R_HEALTH_CERTIFICATION=FAIL"
exit 1
