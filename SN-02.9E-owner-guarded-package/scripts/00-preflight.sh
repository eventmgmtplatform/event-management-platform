#!/usr/bin/env bash
set -euo pipefail

cd /opt/event-management-platform || exit 1

EXPECTED_BRANCH="feature/os-01-02-servicenow-core-foundation"
EXPECTED_HEAD="1d383ffbdb58a10c1ab63350019e476cc2063b65"
MIGRATION="infrastructure/postgres/init/005-integration-command-recovery-lease.sql"

echo "===== SN-02.9E PACKAGE PREFLIGHT ====="
BRANCH="$(git branch --show-current)"
HEAD="$(git rev-parse HEAD)"
echo "BRANCH=${BRANCH}"
echo "HEAD=${HEAD}"

[[ "${BRANCH}" == "${EXPECTED_BRANCH}" ]] || { echo "ASSERTION=FAIL | Unexpected branch"; exit 1; }
[[ "${HEAD}" == "${EXPECTED_HEAD}" ]] || { echo "ASSERTION=FAIL | Unexpected baseline commit"; exit 1; }
[[ -f "${MIGRATION}" ]] || { echo "ASSERTION=FAIL | Migration 005 missing"; exit 1; }

NON_EVIDENCE="$(git status --porcelain | awk '{print $2}' | grep -v '^evidence/' || true)"
[[ "${NON_EVIDENCE}" == "${MIGRATION}" ]] || {
  echo "ASSERTION=FAIL | Unexpected pre-existing changes"
  printf '%s\n' "${NON_EVIDENCE}"
  exit 1
}

COLUMNS="$(
  docker exec event-postgres \
    psql -U eventmanager -d eventmanagement -Atc \
    "SELECT string_agg(column_name, ',' ORDER BY column_name) FROM information_schema.columns WHERE table_schema='event_management' AND table_name='integration_command_execution' AND column_name IN ('claim_owner','lease_expires_at','recovery_count');"
)"
echo "RECOVERY_COLUMNS=${COLUMNS}"
[[ "${COLUMNS}" == "claim_owner,lease_expires_at,recovery_count" ]] || { echo "ASSERTION=FAIL | Active migration 005 missing"; exit 1; }

echo "ASSERTION=PASS | Baseline, worktree and database are ready"
echo "SN_02_9E_PACKAGE_PREFLIGHT=PASS"
