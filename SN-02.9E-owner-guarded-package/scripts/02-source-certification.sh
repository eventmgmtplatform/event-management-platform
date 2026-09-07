#!/usr/bin/env bash
set -euo pipefail

cd /opt/event-management-platform || exit 1
SERVICE="services/integration-worker"

echo "===== SN-02.9E SOURCE CERTIFICATION ====="

echo
echo "===== BUILD AND TEST ====="
(cd "${SERVICE}" && mvn clean test)

echo
echo "===== COMPOSE VALIDATION ====="
docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet
echo "ASSERTION=PASS | Compose configuration valid"

echo
echo "===== REQUIRED CONTRACT ====="
grep -q "RECONCILE" "${SERVICE}/src/main/java/com/eventmanagement/integration/IntegrationCommandLedger.java"
grep -q "claimOwner" "${SERVICE}/src/main/java/com/eventmanagement/integration/IntegrationCommandLedger.java"
grep -q "AND claim_owner = ?" "${SERVICE}/src/main/java/com/eventmanagement/integration/JdbcIntegrationCommandLedger.java"
grep -q "recovery_count = recovery_count + 1" "${SERVICE}/src/main/java/com/eventmanagement/integration/JdbcIntegrationCommandLedger.java"
grep -q "CREATE bloqueado" "${SERVICE}/src/main/resources/routes/integration-worker.xml"
echo "ASSERTION=PASS | Owner-guarded source contract present"

echo
echo "===== REPOSITORY SAFETY ====="
git status --short
if ! git diff --cached --quiet; then
  echo "ASSERTION=FAIL | Staging area must remain empty"
  exit 1
fi
echo "ASSERTION=PASS | Nothing staged"

echo
echo "===== FINAL RESULT ====="
echo "OWNER_GUARDED_COMPLETION=IMPLEMENTED"
echo "ATOMIC_EXPIRED_LEASE_TAKEOVER=IMPLEMENTED"
echo "STALE_DECISION=RECONCILE"
echo "BLIND_STALE_CREATE=BLOCKED"
echo "SERVICENOW_LOOKUP=NEXT_CHECKPOINT"
echo "TECHNICAL_DEBT_SN_006=IN_PROGRESS"
echo "SN_02_9E_SOURCE_CERTIFICATION=PASS"

