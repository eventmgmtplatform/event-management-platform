#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="${PROJECT_ROOT:-/opt/event-management-platform}"
TF_ROOT="${TF_ROOT:-${PROJECT_ROOT}/infrastructure/gcp/terraform}"
TF_ENV_DIR="${TF_ENV_DIR:-${TF_ROOT}/environments/dev}"
PROJECT_ID="${PROJECT_ID:-corded-key-504121-v7}"
PROJECT_NAME="${PROJECT_NAME:-event-management-dev}"
REGION="${REGION:-us-central1}"
TERRAFORM_DEPLOYER="${TERRAFORM_DEPLOYER:-terraform-deployer@corded-key-504121-v7.iam.gserviceaccount.com}"
EXPECTED_BASE_COMMIT="${EXPECTED_BASE_COMMIT:-133af42}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-feature/os-08-10-gcp-cloud-storage}"
EVIDENCE_ROOT="${EVIDENCE_ROOT:-${PROJECT_ROOT}/evidence/os-08-10}"

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

info() {
  printf '\n[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

run_logged() {
  local logfile="$1"
  shift
  mkdir -p "$(dirname "$logfile")"
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@"
  } 2>&1 | tee "$logfile"
}

assert_repo() {
  [[ -d "${PROJECT_ROOT}/.git" ]] || die "Git repository not found: ${PROJECT_ROOT}"
  [[ -d "${TF_ENV_DIR}" ]] || die "Terraform environment not found: ${TF_ENV_DIR}"
}

assert_branch() {
  local branch
  branch="$(git -C "${PROJECT_ROOT}" branch --show-current)"
  [[ "${branch}" == "${EXPECTED_BRANCH}" ]] || die "Expected branch ${EXPECTED_BRANCH}; current branch is ${branch}"
}

assert_clean_worktree() {
  [[ -z "$(git -C "${PROJECT_ROOT}" status --porcelain)" ]] || die "Git worktree is not clean."
}

impersonation_args=(--impersonate-service-account="${TERRAFORM_DEPLOYER}")

PHASE_DIR="${EVIDENCE_ROOT}/02-remediation/${timestamp}"
mkdir -p "${PHASE_DIR}"

for cmd in git gcloud terraform jq grep tee; do
  require_command "${cmd}"
done

assert_repo
assert_branch
assert_clean_worktree

info "This script never enables APIs or grants IAM roles manually."
info "It diagnoses only. Certified modules may be changed only through reviewed Terraform code."

storage_api_enabled=false
if gcloud services list \
  --project="${PROJECT_ID}" \
  --enabled \
  --filter="config.name=storage.googleapis.com" \
  --format='value(config.name)' \
  | grep -qx 'storage.googleapis.com'; then
  storage_api_enabled=true
fi

storage_api_managed=false
if grep -Rqs --include='*.tf' 'storage.googleapis.com' "${TF_ROOT}/bootstrap"; then
  storage_api_managed=true
fi

impersonation_ok=false
if gcloud auth print-access-token "${impersonation_args[@]}" >/dev/null 2>&1; then
  impersonation_ok=true
fi

bucket_list_ok=false
if gcloud storage buckets list \
  --project="${PROJECT_ID}" \
  "${impersonation_args[@]}" \
  --format=json >"${PHASE_DIR}/buckets.json" 2>"${PHASE_DIR}/buckets.stderr"; then
  bucket_list_ok=true
fi

terraform_baseline_ok=false
terraform -chdir="${TF_ENV_DIR}" init -input=false \
  >"${PHASE_DIR}/terraform-init.txt" 2>&1

set +e
terraform -chdir="${TF_ENV_DIR}" plan \
  -input=false \
  -detailed-exitcode \
  -no-color \
  >"${PHASE_DIR}/terraform-plan.txt" 2>&1
plan_rc=$?
set -e
[[ "${plan_rc}" -eq 0 ]] && terraform_baseline_ok=true

jq -n \
  --argjson storage_api_enabled "${storage_api_enabled}" \
  --argjson storage_api_managed_in_bootstrap "${storage_api_managed}" \
  --argjson impersonation_ok "${impersonation_ok}" \
  --argjson bucket_list_ok "${bucket_list_ok}" \
  --argjson terraform_baseline_no_changes "${terraform_baseline_ok}" \
  '{
    storage_api_enabled: $storage_api_enabled,
    storage_api_managed_in_bootstrap: $storage_api_managed_in_bootstrap,
    impersonation_ok: $impersonation_ok,
    bucket_inventory_permission_ok: $bucket_list_ok,
    terraform_baseline_no_changes: $terraform_baseline_no_changes
  }' | tee "${PHASE_DIR}/remediation-assessment.json"

{
  echo "# OS_08_10 remediation assessment"
  echo
  echo "- storage_api_enabled: ${storage_api_enabled}"
  echo "- storage_api_managed_in_bootstrap: ${storage_api_managed}"
  echo "- impersonation_ok: ${impersonation_ok}"
  echo "- bucket_inventory_permission_ok: ${bucket_list_ok}"
  echo "- terraform_baseline_no_changes: ${terraform_baseline_ok}"
  echo
  echo "## Required action"
  if [[ "${storage_api_enabled}" != true ]]; then
    echo "- STOP: storage.googleapis.com is not enabled."
    echo "- Remediate declaratively in Bootstrap, preserving disable_on_destroy = false."
    echo "- Certify the Bootstrap change before continuing."
  elif [[ "${storage_api_managed}" != true ]]; then
    echo "- STOP: storage.googleapis.com is enabled but no declarative Bootstrap reference was found."
    echo "- Reconcile ownership before continuing. Do not enable it manually."
  elif [[ "${impersonation_ok}" != true ]]; then
    echo "- STOP: Terraform Deployer impersonation failed."
    echo "- Repair impersonation using the certified IAM pattern."
  elif [[ "${bucket_list_ok}" != true ]]; then
    echo "- STOP: Terraform Deployer cannot inventory buckets."
    echo "- Inspect ${PHASE_DIR}/buckets.stderr."
    echo "- Determine the exact missing permission before changing modules/iam."
    echo "- Use google_project_iam_member only; do not use binding or policy resources."
  elif [[ "${terraform_baseline_ok}" != true ]]; then
    echo "- STOP: certified Terraform baseline is not clean."
    echo "- Reconcile the drift or pending configuration before OS_08_10."
  else
    echo "- PASS: no prerequisite remediation is currently required."
    echo "- Continue with reviewed Cloud Storage module implementation."
  fi
} | tee "${PHASE_DIR}/remediation-report.md"

if [[ "${storage_api_enabled}" == true \
   && "${storage_api_managed}" == true \
   && "${impersonation_ok}" == true \
   && "${bucket_list_ok}" == true \
   && "${terraform_baseline_ok}" == true ]]; then
  touch "${EVIDENCE_ROOT}/REMEDIATION_GATE_PASSED"
  info "Remediation gate passed"
  exit 0
fi

rm -f "${EVIDENCE_ROOT}/REMEDIATION_GATE_PASSED"
die "Remediation gate did not pass. Review ${PHASE_DIR}/remediation-report.md"
