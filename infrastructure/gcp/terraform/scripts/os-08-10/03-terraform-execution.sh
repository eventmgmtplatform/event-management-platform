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

PHASE_DIR="${EVIDENCE_ROOT}/03-execution/${timestamp}"
mkdir -p "${PHASE_DIR}"

for cmd in git terraform tee; do
  require_command "${cmd}"
done

assert_repo
assert_branch

[[ -f "${EVIDENCE_ROOT}/REMEDIATION_GATE_PASSED" ]] \
  || die "Remediation gate not passed. Run 02-remediation-gate.sh first."

MODULE_DIR="${TF_ROOT}/modules/cloud-storage"
COMPOSITION_FILE="${TF_ENV_DIR}/cloud-storage.tf"

for required_file in \
  "${MODULE_DIR}/README.md" \
  "${MODULE_DIR}/versions.tf" \
  "${MODULE_DIR}/variables.tf" \
  "${MODULE_DIR}/locals.tf" \
  "${MODULE_DIR}/main.tf" \
  "${MODULE_DIR}/outputs.tf" \
  "${COMPOSITION_FILE}"; do
  [[ -f "${required_file}" ]] || die "Required implementation file missing: ${required_file}"
done

info "Recording implementation diff before execution"
git -C "${PROJECT_ROOT}" status \
  | tee "${PHASE_DIR}/01-git-status-before.txt"

git -C "${PROJECT_ROOT}" diff \
  | tee "${PHASE_DIR}/02-git-diff-before.patch"

git -C "${PROJECT_ROOT}" diff --check \
  2>&1 | tee "${PHASE_DIR}/03-git-diff-check.txt"

info "Formatting Terraform"
terraform -chdir="${TF_ROOT}" fmt -recursive -check=false \
  2>&1 | tee "${PHASE_DIR}/04-terraform-fmt.txt"

info "Initializing Terraform"
terraform -chdir="${TF_ENV_DIR}" init -input=false \
  2>&1 | tee "${PHASE_DIR}/05-terraform-init.txt"

info "Validating Terraform"
terraform -chdir="${TF_ENV_DIR}" validate -no-color \
  2>&1 | tee "${PHASE_DIR}/06-terraform-validate.txt"

info "Creating reviewed execution plan"
terraform -chdir="${TF_ENV_DIR}" plan \
  -input=false \
  -no-color \
  -out="${PHASE_DIR}/os-08-10.tfplan" \
  2>&1 | tee "${PHASE_DIR}/07-terraform-plan.txt"

terraform -chdir="${TF_ENV_DIR}" show -json "${PHASE_DIR}/os-08-10.tfplan" \
  > "${PHASE_DIR}/08-terraform-plan.json"

terraform -chdir="${TF_ENV_DIR}" show -no-color "${PHASE_DIR}/os-08-10.tfplan" \
  > "${PHASE_DIR}/09-terraform-plan-rendered.txt"

if [[ "${OS_08_10_APPROVE_APPLY:-NO}" != "YES" ]]; then
  die "Plan generated but not applied. Review evidence, then rerun with OS_08_10_APPROVE_APPLY=YES."
fi

info "Applying the exact saved plan"
terraform -chdir="${TF_ENV_DIR}" apply \
  -input=false \
  -no-color \
  -auto-approve \
  "${PHASE_DIR}/os-08-10.tfplan" \
  2>&1 | tee "${PHASE_DIR}/10-terraform-apply.txt"

info "Capturing immediate state and outputs"
terraform -chdir="${TF_ENV_DIR}" state list \
  2>&1 | tee "${PHASE_DIR}/11-terraform-state-list.txt"

terraform -chdir="${TF_ENV_DIR}" output -json \
  2>&1 | tee "${PHASE_DIR}/12-terraform-outputs.json"

info "Execution completed"
echo "Evidence: ${PHASE_DIR}"
