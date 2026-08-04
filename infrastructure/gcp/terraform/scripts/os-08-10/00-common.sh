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
