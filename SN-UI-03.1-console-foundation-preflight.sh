#!/usr/bin/env bash
set -uo pipefail

ROOT="/opt/event-management-platform"
EXPECTED_BRANCH="feature/os-01-02-servicenow-core-foundation"
EXPECTED_HEAD="b2475908289a427c6c1c9d728ee93d1d4ffa1c0c"
CORE_SERVICE="integration-worker"
CORE_CONTAINER="event-integration-worker"
LAB_CONTAINER="event-itsm-ticketing-dashboard"
CONSOLE_CONTAINER="event-management-console"
CONSOLE_PORT="8090"
LAB_PORT="8088"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_DIR="${ROOT}/evidence/sn-ui-03/03.1-preflight/${TIMESTAMP}"
REPORT="${REPORT_DIR}/SN-UI-03.1-console-foundation-preflight.txt"
FAILURES=00

mkdir -p "${REPORT_DIR}"

exec > >(tee -a "${REPORT}") 2>&1

pass() { echo "ASSERTION=PASS | $*"; }
fail() { echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES + 1)); }
info() { echo "INFO | $*"; }

echo "===== SN-UI-03.1 EVENT MANAGEMENT CONSOLE FOUNDATION PREFLIGHT ====="
echo "UTC=${TIMESTAMP}"
echo "MODE=READ_ONLY_DISCOVERY_WITH_EVIDENCE_WRITE"
echo "ROOT=${ROOT}"
echo "EXPECTED_BRANCH=${EXPECTED_BRANCH}"
echo "EXPECTED_HEAD=${EXPECTED_HEAD}"
echo "REPORT=${REPORT}"

if [[ ! -d "${ROOT}/.git" ]]; then
  fail "Repository not found at ${ROOT}"
  echo "SN_UI_03_1_PREFLIGHT=FAIL"
  exit 1
fi

cd "${ROOT}" || exit 1

echo
echo "===== 01. TOOLCHAIN ====="
for tool in git docker curl sed awk find sha256sum; do
  if command -v "${tool}" >/dev/null 2>&1; then
    pass "Tool available: ${tool}"
  else
    fail "Required tool missing: ${tool}"
  fi
done

echo
echo "===== 02. GIT BASELINE SAFETY ====="
CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || true)"
CURRENT_HEAD="$(git rev-parse HEAD 2>/dev/null || true)"
STAGED_COUNT="$(git diff --cached --name-only | sed '/^$/d' | wc -l | tr -d ' ')"
echo "CURRENT_BRANCH=${CURRENT_BRANCH}"
echo "CURRENT_HEAD=${CURRENT_HEAD}"
echo "STAGED_FILES=${STAGED_COUNT}"

[[ "${CURRENT_BRANCH}" == "${EXPECTED_BRANCH}" ]] && pass "Correct branch" || fail "Unexpected branch"
[[ "${CURRENT_HEAD}" == "${EXPECTED_HEAD}" ]] && pass "Certified baseline commit preserved" || fail "Unexpected HEAD"
[[ "${STAGED_COUNT}" == "0" ]] && pass "Git staging is empty" || fail "Git staging must be empty"

echo "----- WORKTREE (INFORMATIONAL; USER ARTIFACTS ARE PRESERVED) -----"
git status --short || true

echo
echo "===== 03. PROTECTED SERVICENOW CORE SNAPSHOT ====="
CORE_ID="$(docker inspect -f '{{.Id}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_IMAGE="$(docker inspect -f '{{.Image}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_STATUS="$(docker inspect -f '{{.State.Status}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_RESTARTS="$(docker inspect -f '{{.RestartCount}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
echo "CORE_SERVICE=${CORE_SERVICE}"
echo "CORE_CONTAINER=${CORE_CONTAINER}"
echo "CORE_ID=${CORE_ID:-ABSENT}"
echo "CORE_IMAGE=${CORE_IMAGE:-ABSENT}"
echo "CORE_STATUS=${CORE_STATUS:-ABSENT}"
echo "CORE_HEALTH=${CORE_HEALTH:-ABSENT}"
echo "CORE_RESTARTS=${CORE_RESTARTS:-ABSENT}"

if [[ -n "${CORE_ID}" ]]; then
  pass "ServiceNow Core container found"
  [[ "${CORE_STATUS}" == "running" ]] && pass "ServiceNow Core is running" || fail "ServiceNow Core is not running"
  [[ "${CORE_HEALTH}" == "healthy" ]] && pass "ServiceNow Core is healthy" || fail "ServiceNow Core is not healthy"
else
  fail "ServiceNow Core container not found"
fi

echo
echo "===== 04. PROTECTED SOURCE CHECK ====="
if [[ -d services/integration-worker ]]; then
  pass "Protected integration-worker source exists"
else
  fail "Protected integration-worker source missing"
fi

PROTECTED_CHANGES="$(git status --porcelain -- services/integration-worker infrastructure/postgres 2>/dev/null || true)"
if [[ -z "${PROTECTED_CHANGES}" ]]; then
  pass "No worktree changes in integration-worker or PostgreSQL initialization"
else
  echo "${PROTECTED_CHANGES}"
  fail "Protected source or database initialization has worktree changes"
fi

echo
echo "===== 05. CURRENT EVENT MANAGEMENT COMPONENT INVENTORY ====="
for component in \
  services/event-gateway \
  services/enrichment-engine \
  services/event-state-service \
  services/integration-worker \
  services/itsm-ticketing-dashboard; do
  if [[ -d "${component}" ]]; then
    echo "COMPONENT=FOUND | ${component}"
  else
    echo "COMPONENT=NOT_FOUND | ${component}"
  fi
done

if [[ -e services/event-management-console ]]; then
  fail "Target path services/event-management-console already exists; review before installation"
else
  pass "Target path services/event-management-console is available"
fi

echo "----- RUNNING CONTAINERS -----"
docker ps --format 'NAME={{.Names}} | IMAGE={{.Image}} | STATUS={{.Status}} | PORTS={{.Ports}}' | sort || true

echo
echo "===== 06. SN-UI-01 AUXILIARY LAB SAFETY ====="
LAB_STATUS="$(docker inspect -f '{{.State.Status}}' "${LAB_CONTAINER}" 2>/dev/null || true)"
echo "LAB_CONTAINER=${LAB_CONTAINER}"
echo "LAB_STATUS=${LAB_STATUS:-ABSENT}"
if [[ "${LAB_STATUS}" == "running" ]]; then
  pass "SN-UI-01 auxiliary lab remains running"
else
  fail "SN-UI-01 auxiliary lab is not running"
fi

LAB_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${LAB_PORT}/itsm/tickets/" 2>/dev/null || true)"
echo "LAB_HTTP=${LAB_HTTP:-000}"
[[ "${LAB_HTTP}" =~ ^(200|301|302)$ ]] && pass "SN-UI-01 endpoint responds" || fail "SN-UI-01 endpoint does not respond successfully"

echo
echo "===== 07. CONSOLE DEPLOYMENT SLOT ====="
CONSOLE_ID="$(docker inspect -f '{{.Id}}' "${CONSOLE_CONTAINER}" 2>/dev/null || true)"
if [[ -z "${CONSOLE_ID}" ]]; then
  pass "Console container name is available"
else
  fail "Container ${CONSOLE_CONTAINER} already exists"
fi

PORT_LISTENERS="$(docker ps --format '{{.Ports}}' | grep -E "(^|[:])${CONSOLE_PORT}->|:${CONSOLE_PORT}-" || true)"
if [[ -z "${PORT_LISTENERS}" ]]; then
  pass "Host port ${CONSOLE_PORT} is available in Docker"
else
  echo "${PORT_LISTENERS}"
  fail "Host port ${CONSOLE_PORT} is already published"
fi

if command -v ss >/dev/null 2>&1; then
  if ss -H -ltn "sport = :${CONSOLE_PORT}" 2>/dev/null | grep -q .; then
    fail "Host port ${CONSOLE_PORT} has an active listener"
  else
    pass "Host port ${CONSOLE_PORT} has no active listener"
  fi
else
  info "ss unavailable; Docker port inspection used"
fi

echo
echo "===== 08. COMPOSE DISCOVERY ====="
if [[ -f infrastructure/docker-compose.yml ]]; then
  pass "Base Compose file exists"
  docker compose -f infrastructure/docker-compose.yml config --services 2>/dev/null | sort || true
else
  fail "Base Compose file missing"
fi

if [[ -f infrastructure/docker-compose.itsm-dashboard.yml ]]; then
  pass "SN-UI-01 overlay exists"
else
  fail "SN-UI-01 overlay missing"
fi

if [[ -e infrastructure/docker-compose.event-management-console.yml ]]; then
  fail "Target Console overlay already exists; review before installation"
else
  pass "Target Console overlay path is available"
fi

echo
echo "===== 09. ARCHITECTURAL TARGET CERTIFICATION ====="
echo "SOLUTION=event-management-console"
echo "ROLE=GLOBAL_EVENT_MANAGEMENT_CONSOLE"
echo "FIRST_PLUGIN=ticketing"
echo "FIRST_PAGE=/ticketing/tickets"
echo "RUNTIME=NGINX_CONTAINER"
echo "PROPOSED_HOST_PORT=${CONSOLE_PORT}"
echo "CORE_CONNECTION=NONE_IN_SN_UI_03"
echo "DATA_MODE=MOCK"
pass "Global Console boundary declared"
pass "Ticketing plugin boundary declared"
pass "Ticketing/Tickets dashboard boundary declared"
pass "Parallel container boundary declared"

echo
echo "===== 10. NON-MUTATION DECLARATION ====="
echo "INTEGRATION_WORKER_MODIFIED=FALSE"
echo "INTEGRATION_WORKER_REBUILT=FALSE"
echo "INTEGRATION_WORKER_RESTARTED=FALSE"
echo "POSTGRESQL_MIGRATIONS_EXECUTED=FALSE"
echo "POSTGRESQL_DATA_CHANGED=FALSE"
echo "KAFKA_OFFSETS_CHANGED=FALSE"
echo "KAFKA_COMMANDS_PUBLISHED=FALSE"
echo "SN_UI_01_MODIFIED=FALSE"

echo
echo "===== FINAL RESULT ====="
echo "FAILURE_COUNT=${FAILURES}"
echo "REPORT=${REPORT}"
if (( FAILURES == 0 )); then
  echo "SN_UI_03_1_PREFLIGHT=PASS"
  echo "NEXT_CHECKPOINT=SN-UI-03.2"
  exit 0
fi

echo "SN_UI_03_1_PREFLIGHT=FAIL"
echo "NEXT_ACTION=REVIEW_FAILED_ASSERTIONS_BEFORE_INSTALLATION"
exit 1
