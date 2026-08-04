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

PHASE_DIR="${EVIDENCE_ROOT}/01-prerequisites-and-inventory/${timestamp}"

for cmd in git gcloud terraform curl jq grep sed find tee; do
  require_command "${cmd}"
done

assert_repo
assert_branch
assert_clean_worktree

mkdir -p "${PHASE_DIR}"

info "Recording local tool versions"
{
  git --version
  gcloud version
  terraform version
  curl --version | head -1
  jq --version
} | tee "${PHASE_DIR}/00-tool-versions.txt"

info "Recording Git baseline"
{
  echo "project_root=${PROJECT_ROOT}"
  echo "expected_branch=${EXPECTED_BRANCH}"
  echo "expected_base_commit=${EXPECTED_BASE_COMMIT}"
  echo
  git -C "${PROJECT_ROOT}" status
  echo
  git -C "${PROJECT_ROOT}" branch --show-current
  echo
  git -C "${PROJECT_ROOT}" log -5 --oneline --decorate
  echo
  git -C "${PROJECT_ROOT}" branch --list
} | tee "${PHASE_DIR}/01-git-baseline.txt"

current_branch="$(git -C "${PROJECT_ROOT}" branch --show-current)"
if [[ "${current_branch}" != "${EXPECTED_BRANCH}" ]]; then
  if git -C "${PROJECT_ROOT}" show-ref --verify --quiet "refs/heads/${EXPECTED_BRANCH}"; then
    die "Branch ${EXPECTED_BRANCH} already exists. Switch to it manually after reviewing the worktree."
  fi

  [[ -z "$(git -C "${PROJECT_ROOT}" status --porcelain)" ]] \
    || die "Worktree is not clean. Commit or stash changes before creating the OS_08_10 branch."

  git -C "${PROJECT_ROOT}" merge-base --is-ancestor "${EXPECTED_BASE_COMMIT}" HEAD \
    || die "Commit ${EXPECTED_BASE_COMMIT} is not an ancestor of the current HEAD."

  info "Creating official OS_08_10 branch"
  git -C "${PROJECT_ROOT}" switch -c "${EXPECTED_BRANCH}"
fi

info "Recording active gcloud configuration"
run_logged "${PHASE_DIR}/02-gcloud-config.txt" gcloud config list
run_logged "${PHASE_DIR}/03-gcloud-auth-list.txt" gcloud auth list

info "Validating project identity"
run_logged "${PHASE_DIR}/04-project.json" \
  gcloud projects describe "${PROJECT_ID}" \
  --format=json

info "Validating Terraform Deployer impersonation"
gcloud auth print-access-token "${impersonation_args[@]}" >/dev/null
echo "Impersonation OK: ${TERRAFORM_DEPLOYER}" \
  | tee "${PHASE_DIR}/05-impersonation.txt"

info "Inventorying enabled Storage API"
run_logged "${PHASE_DIR}/06-storage-api.json" \
  gcloud services list \
  --project="${PROJECT_ID}" \
  --enabled \
  --filter="config.name=storage.googleapis.com" \
  --format=json

info "Finding declarative API management"
{
  grep -Rni --include='*.tf' 'storage.googleapis.com' \
    "${TF_ROOT}/bootstrap" \
    "${TF_ROOT}/modules" \
    "${TF_ROOT}/environments" || true
  echo
  grep -Rni --include='*.tf' \
    'google_project_service\|required_apis\|disable_on_destroy' \
    "${TF_ROOT}/bootstrap" || true
} | tee "${PHASE_DIR}/07-storage-api-terraform-references.txt"

info "Inventorying existing buckets with impersonation"
set +e
gcloud storage buckets list \
  --project="${PROJECT_ID}" \
  "${impersonation_args[@]}" \
  --format=json \
  >"${PHASE_DIR}/08-existing-buckets.json" \
  2>"${PHASE_DIR}/08-existing-buckets.stderr"
bucket_list_rc=$?
set -e

if [[ "${bucket_list_rc}" -eq 0 ]]; then
  echo "BUCKET_LIST_STATUS=OK" \
    | tee "${PHASE_DIR}/08-existing-buckets-status.txt"
else
  {
    echo "BUCKET_LIST_STATUS=REMEDIATION_REQUIRED"
    echo "BUCKET_LIST_EXIT_CODE=${bucket_list_rc}"
    echo "EXPECTED_MISSING_PERMISSION=storage.buckets.list"
  } | tee "${PHASE_DIR}/08-existing-buckets-status.txt"

  cat "${PHASE_DIR}/08-existing-buckets.stderr" >&2
fi

set +e
gcloud storage ls \
  --project="${PROJECT_ID}" \
  "${impersonation_args[@]}" \
  >"${PHASE_DIR}/09-existing-buckets-ls.txt" \
  2>"${PHASE_DIR}/09-existing-buckets-ls.stderr"
storage_ls_rc=$?
set -e

if [[ "${storage_ls_rc}" -eq 0 ]]; then
  echo "STORAGE_LS_STATUS=OK" \
    | tee "${PHASE_DIR}/09-existing-buckets-ls-status.txt"
else
  {
    echo "STORAGE_LS_STATUS=REMEDIATION_REQUIRED"
    echo "STORAGE_LS_EXIT_CODE=${storage_ls_rc}"
  } | tee "${PHASE_DIR}/09-existing-buckets-ls-status.txt"

  cat "${PHASE_DIR}/09-existing-buckets-ls.stderr" >&2
fi

info "Inventorying Terraform Deployer IAM"
run_logged "${PHASE_DIR}/10-terraform-deployer-iam.json" \
  gcloud projects get-iam-policy "${PROJECT_ID}" \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:${TERRAFORM_DEPLOYER}" \
  --format=json

info "Finding IAM and Storage references in Terraform"
{
  grep -Rni --include='*.tf' \
    "${TERRAFORM_DEPLOYER}\|artifactregistry.admin\|compute.networkAdmin\|compute.securityAdmin\|roles/storage\|google_storage" \
    "${TF_ROOT}/modules/iam" \
    "${TF_ENV_DIR}" || true
} | tee "${PHASE_DIR}/11-iam-storage-terraform-references.txt"

info "Recording Terraform module and environment structure"
{
  find "${TF_ROOT}/modules" -maxdepth 2 -type f -print | sort
  echo
  find "${TF_ENV_DIR}" -maxdepth 1 -type f -name '*.tf' -print | sort
} | tee "${PHASE_DIR}/12-terraform-structure.txt"

info "Initializing Terraform without changing infrastructure"
terraform -chdir="${TF_ENV_DIR}" init -input=false \
  2>&1 | tee "${PHASE_DIR}/13-terraform-init.txt"

info "Validating certified baseline"
terraform -chdir="${TF_ENV_DIR}" validate -no-color \
  2>&1 | tee "${PHASE_DIR}/14-terraform-validate.txt"

terraform -chdir="${TF_ENV_DIR}" providers \
  2>&1 | tee "${PHASE_DIR}/15-terraform-providers.txt"

terraform -chdir="${TF_ENV_DIR}" state list \
  2>&1 | tee "${PHASE_DIR}/16-terraform-state-list.txt"

terraform -chdir="${TF_ENV_DIR}" output -json \
  2>&1 | tee "${PHASE_DIR}/17-terraform-outputs.json"

set +e
terraform -chdir="${TF_ENV_DIR}" plan \
  -input=false \
  -detailed-exitcode \
  -no-color \
  -out="${PHASE_DIR}/baseline.tfplan" \
  2>&1 | tee "${PHASE_DIR}/18-baseline-plan.txt"
plan_rc=${PIPESTATUS[0]}
set -e

case "${plan_rc}" in
  0)
    echo "BASELINE_STATUS=NO_CHANGES" | tee "${PHASE_DIR}/19-baseline-status.txt"
    ;;
  2)
    echo "BASELINE_STATUS=CHANGES_DETECTED" | tee "${PHASE_DIR}/19-baseline-status.txt"
    die "The certified baseline has pending changes. Run remediation analysis before implementing Cloud Storage."
    ;;
  *)
    echo "BASELINE_STATUS=PLAN_FAILED" | tee "${PHASE_DIR}/19-baseline-status.txt"
    die "Terraform baseline plan failed."
    ;;
esac

info "Generating machine-readable inventory summary"
api_managed=false
grep -Rqs --include='*.tf' 'storage.googleapis.com' "${TF_ROOT}/bootstrap" && api_managed=true

storage_api_enabled=false
jq -e 'length > 0' "${PHASE_DIR}/06-storage-api.json" >/dev/null 2>&1 && storage_api_enabled=true

jq -n \
  --arg project_id "${PROJECT_ID}" \
  --arg project_name "${PROJECT_NAME}" \
  --arg region "${REGION}" \
  --arg branch "${EXPECTED_BRANCH}" \
  --arg deployer "${TERRAFORM_DEPLOYER}" \
  --arg evidence_dir "${PHASE_DIR}" \
  --argjson storage_api_enabled "${storage_api_enabled}" \
  --argjson storage_api_managed_in_bootstrap "${api_managed}" \
  '{
    project_id: $project_id,
    project_name: $project_name,
    region: $region,
    branch: $branch,
    terraform_deployer: $deployer,
    storage_api_enabled: $storage_api_enabled,
    storage_api_managed_in_bootstrap: $storage_api_managed_in_bootstrap,
    baseline_plan: "NO_CHANGES",
    evidence_directory: $evidence_dir
  }' | tee "${PHASE_DIR}/inventory-summary.json"

info "Prerequisites and inventory completed successfully"
echo "Evidence: ${PHASE_DIR}"
