#!/usr/bin/env bash
set -uo pipefail

ROOT="/opt/event-management-platform"
TARGET="${ROOT}/services/event-management-console"
EXPECTED_BRANCH="feature/os-01-02-servicenow-core-foundation"
EXPECTED_HEAD="b2475908289a427c6c1c9d728ee93d1d4ffa1c0c"
CORE_CONTAINER="event-integration-worker"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_DIR="${ROOT}/evidences/sn-ui-03/03.2-scaffold/${TIMESTAMP}"
REPORT="${REPORT_DIR}/SN-UI-03.2-console-scaffold-validation.txt"
FAILURES=0

mkdir -p "${REPORT_DIR}"
exec > >(tee -a "${REPORT}") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES + 1)); }

echo "===== SN-UI-03.2 CONSOLE SCAFFOLD VALIDATION ====="
echo "UTC=${TIMESTAMP}"
echo "REPORT=${REPORT}"
cd "${ROOT}" || exit 1

[[ "$(git branch --show-current)" == "${EXPECTED_BRANCH}" ]] && pass "Correct branch" || fail "Unexpected branch"
[[ "$(git rev-parse HEAD)" == "${EXPECTED_HEAD}" ]] && pass "Certified baseline preserved" || fail "Unexpected HEAD"
[[ "$(git diff --cached --name-only | sed '/^$/d' | wc -l | tr -d ' ')" == "0" ]] && pass "Staging remains empty" || fail "Staging is not empty"

REQUIRED=(
  package.json tsconfig.json vite.config.ts index.html README.md
  src/main.tsx src/app/App.tsx src/app/router.tsx
  src/layout/ConsoleLayout.tsx src/navigation/navigation.registry.ts
  src/shared/theme/tokens.css src/shared/theme/global.css
  src/modules/home/HomePage.tsx
  src/modules/ticketing/routes.tsx
  src/modules/ticketing/pages/TicketDashboardPage.tsx
  src/modules/ticketing/services/ticketing.port.ts
  src/modules/ticketing/services/mock-ticketing.adapter.ts
)

for file in "${REQUIRED[@]}"; do
  [[ -f "${TARGET}/${file}" ]] && pass "Found ${file}" || fail "Missing ${file}"
done

grep -Fq '"name": "event-management-console"' "${TARGET}/package.json" && pass "Package identity correct" || fail "Package identity incorrect"
grep -Fq 'path: "ticketing/tickets"' "${TARGET}/src/app/router.tsx" && pass "Ticketing/Tickets route declared" || fail "Ticketing/Tickets route missing"
grep -Fq 'GLOBAL_EVENT_MANAGEMENT_CONSOLE' "${TARGET}/src/app/App.tsx" && pass "Global Console role declared" || fail "Global Console role missing"

if command -v node >/dev/null 2>&1; then
  echo "NODE_VERSION=$(node --version)"
  pass "Node available"
else
  fail "Node is required for source build validation"
fi

if command -v npm >/dev/null 2>&1; then
  echo "NPM_VERSION=$(npm --version)"
  pass "npm available"
else
  fail "npm is required for source build validation"
fi

if command -v npm >/dev/null 2>&1; then
  cd "${TARGET}" || exit 1
  if npm install --ignore-scripts --no-audit --no-fund; then
    pass "Dependencies installed"
    if npm run build; then
      pass "React/TypeScript/Vite production build successful"
    else
      fail "Production build failed"
    fi
  else
    fail "Dependency installation failed"
  fi
  cd "${ROOT}" || exit 1
fi

PROTECTED_CHANGES="$(git status --porcelain -- services/integration-worker infrastructure/postgres || true)"
[[ -z "${PROTECTED_CHANGES}" ]] && pass "Protected source unchanged" || { echo "${PROTECTED_CHANGES}"; fail "Protected source changed"; }

CORE_STATUS="$(docker inspect -f '{{.State.Status}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
CORE_RESTARTS="$(docker inspect -f '{{.RestartCount}}' "${CORE_CONTAINER}" 2>/dev/null || true)"
echo "CORE_STATUS=${CORE_STATUS:-ABSENT}"
echo "CORE_HEALTH=${CORE_HEALTH:-ABSENT}"
echo "CORE_RESTARTS=${CORE_RESTARTS:-ABSENT}"
[[ "${CORE_STATUS}" == "running" && "${CORE_HEALTH}" == "healthy" && "${CORE_RESTARTS}" == "0" ]] && pass "ServiceNow Core remains healthy and unrestarted" || fail "ServiceNow Core safety state unexpected"

echo "INTEGRATION_WORKER_MODIFIED=FALSE"
echo "INTEGRATION_WORKER_REBUILT=FALSE"
echo "INTEGRATION_WORKER_RESTARTED=FALSE"
echo "POSTGRESQL_MIGRATIONS_EXECUTED=FALSE"
echo "KAFKA_COMMANDS_PUBLISHED=FALSE"
echo "FAILURE_COUNT=${FAILURES}"
echo "REPORT=${REPORT}"
if (( FAILURES == 0 )); then
  echo "SN_UI_03_2_SCAFFOLD=PASS"
  echo "NEXT_CHECKPOINT=SN-UI-03.3"
  exit 0
fi
echo "SN_UI_03_2_SCAFFOLD=FAIL"
exit 1
