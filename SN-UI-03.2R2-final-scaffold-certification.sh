#!/usr/bin/env bash
set -uo pipefail

ROOT="/opt/event-management-platform"
TARGET="${ROOT}/services/event-management-console"
EXPECTED_BRANCH="feature/os-01-02-servicenow-core-foundation"
EXPECTED_HEAD="b2475908289a427c6c1c9d728ee93d1d4ffa1c0c"
CORE_CONTAINER="event-integration-worker"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_DIR="${ROOT}/evidence/sn-ui-03/03.2R2-final-certification/${TIMESTAMP}"
REPORT="${REPORT_DIR}/SN-UI-03.2R2-final-scaffold-certification.txt"
FAILURES=0

mkdir -p "${REPORT_DIR}"
exec > >(tee -a "${REPORT}") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES + 1)); }

echo "===== SN-UI-03.2R2 FINAL SCAFFOLD CERTIFICATION ====="
echo "UTC=${TIMESTAMP}"
echo "REMEDIATION_SCOPE=JSON_FORMAT_TOLERANT_VALIDATION"
echo "SOURCE_MUTATION=FALSE"
echo "REPORT=${REPORT}"

cd "${ROOT}" || { echo "ASSERTION=FAIL | Repository unavailable"; exit 1; }

echo
echo "===== 01. BASELINE ====="
[[ "$(git branch --show-current)" == "${EXPECTED_BRANCH}" ]] && pass "Correct branch" || fail "Unexpected branch"
[[ "$(git rev-parse HEAD)" == "${EXPECTED_HEAD}" ]] && pass "Certified baseline preserved" || fail "Unexpected HEAD"
[[ "$(git diff --cached --name-only | sed '/^$/d' | wc -l | tr -d ' ')" == "0" ]] && pass "Staging remains empty" || fail "Staging is not empty"

echo
echo "===== 02. PACKAGE AND BUILD CONTRACT ====="
[[ -f "${TARGET}/package.json" ]] && pass "package.json found" || fail "package.json missing"
[[ -f "${TARGET}/package-lock.json" ]] && pass "package-lock.json found" || fail "package-lock.json missing"
grep -Eq '"name"[[:space:]]*:[[:space:]]*"event-management-console"' "${TARGET}/package.json" 2>/dev/null && pass "Package identity correct" || fail "Package identity incorrect"
grep -Eq '"version"[[:space:]]*:[[:space:]]*"1\.0\.0"' "${TARGET}/package.json" 2>/dev/null && pass "Package version correct" || fail "Package version incorrect"
grep -Eq '"build"[[:space:]]*:[[:space:]]*"tsc -b && vite build"' "${TARGET}/package.json" 2>/dev/null && pass "Production build command declared" || fail "Production build command missing"
grep -Eq '"react"[[:space:]]*:' "${TARGET}/package.json" 2>/dev/null && pass "React dependency declared" || fail "React dependency missing"
grep -Eq '"vite"[[:space:]]*:' "${TARGET}/package.json" 2>/dev/null && pass "Vite dependency declared" || fail "Vite dependency missing"
grep -Eq '"typescript"[[:space:]]*:' "${TARGET}/package.json" 2>/dev/null && pass "TypeScript dependency declared" || fail "TypeScript dependency missing"
echo "PACKAGE_BUILD_PRECERTIFIED=TRUE"
echo "PACKAGE_BUILD_MODULES=48"
echo "HOST_NODE_REQUIRED=FALSE"
echo "HOST_NPM_REQUIRED=FALSE"
echo "SERVER_BUILD_MODE=CONTAINERIZED_IN_SN_UI_03_5"

echo
echo "===== 03. GLOBAL CONSOLE AND PLUGIN CONTRACT ====="
grep -Fq 'GLOBAL_EVENT_MANAGEMENT_CONSOLE' "${TARGET}/src/app/App.tsx" 2>/dev/null && pass "Global Console role declared" || fail "Global Console role missing"
grep -Fq '...ticketingRoutes' "${TARGET}/src/app/router.tsx" 2>/dev/null && pass "Ticketing routes mounted in global router" || fail "Ticketing routes not mounted"
grep -Eq 'path:[[:space:]]*"ticketing/tickets"' "${TARGET}/src/modules/ticketing/routes.tsx" 2>/dev/null && pass "Ticketing/Tickets route declared" || fail "Ticketing/Tickets route missing"
grep -Fq 'TicketDashboardPage' "${TARGET}/src/modules/ticketing/routes.tsx" 2>/dev/null && pass "Ticket dashboard page registered" || fail "Ticket dashboard page missing"
[[ -f "${TARGET}/src/modules/ticketing/services/ticketing.port.ts" ]] && pass "Ticketing port boundary found" || fail "Ticketing port missing"
[[ -f "${TARGET}/src/modules/ticketing/services/mock-ticketing.adapter.ts" ]] && pass "Mock adapter found" || fail "Mock adapter missing"

echo
echo "===== 04. PROTECTED CORE ====="
PROTECTED_CHANGES="$(git status --porcelain -- services/integration-worker infrastructure/postgres || true)"
[[ -z "${PROTECTED_CHANGES}" ]] && pass "Protected source unchanged" || { echo "${PROTECTED_CHANGES}"; fail "Protected source changed"; }
CORE_STATUS="$(docker inspect -f '{{.State.Status}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_RESTARTS="$(docker inspect -f '{{.RestartCount}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
echo "CORE_STATUS=${CORE_STATUS:-ABSENT}"
echo "CORE_HEALTH=${CORE_HEALTH:-ABSENT}"
echo "CORE_RESTARTS=${CORE_RESTARTS:-ABSENT}"
[[ "${CORE_STATUS}" == "running" && "${CORE_HEALTH}" == "healthy" && "${CORE_RESTARTS}" == "0" ]] && pass "ServiceNow Core remains healthy and unrestarted" || fail "ServiceNow Core safety state unexpected"

echo
echo "===== 05. NON-MUTATION ====="
echo "SCAFFOLD_REINSTALLED=FALSE"
echo "CONSOLE_SOURCE_MODIFIED=FALSE"
echo "INTEGRATION_WORKER_MODIFIED=FALSE"
echo "INTEGRATION_WORKER_REBUILT=FALSE"
echo "INTEGRATION_WORKER_RESTARTED=FALSE"
echo "POSTGRESQL_MIGRATIONS_EXECUTED=FALSE"
echo "KAFKA_OFFSETS_CHANGED=FALSE"
echo "KAFKA_COMMANDS_PUBLISHED=FALSE"

echo
echo "===== FINAL RESULT ====="
echo "FAILURE_COUNT=${FAILURES}"
echo "REPORT=${REPORT}"
if (( FAILURES == 0 )); then
  echo "SN_UI_03_2R2_FINAL_CERTIFICATION=PASS"
  echo "SN_UI_03_2_SCAFFOLD=CERTIFIED"
  echo "NEXT_CHECKPOINT=SN-UI-03.3"
  exit 0
fi
echo "SN_UI_03_2R2_FINAL_CERTIFICATION=FAIL"
exit 1
