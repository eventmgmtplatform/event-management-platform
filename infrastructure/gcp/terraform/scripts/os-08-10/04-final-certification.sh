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

PHASE_DIR="${EVIDENCE_ROOT}/04-final-certification/${timestamp}"
mkdir -p "${PHASE_DIR}"

for cmd in git gcloud terraform curl jq grep tee sha256sum; do
  require_command "${cmd}"
done

assert_repo
assert_branch

info "Terraform final validation"
terraform -chdir="${TF_ROOT}" fmt -recursive -check \
  2>&1 | tee "${PHASE_DIR}/01-terraform-fmt-check.txt"

terraform -chdir="${TF_ENV_DIR}" init -input=false \
  2>&1 | tee "${PHASE_DIR}/02-terraform-init.txt"

terraform -chdir="${TF_ENV_DIR}" validate -no-color \
  2>&1 | tee "${PHASE_DIR}/03-terraform-validate.txt"

set +e
terraform -chdir="${TF_ENV_DIR}" plan \
  -input=false \
  -detailed-exitcode \
  -no-color \
  2>&1 | tee "${PHASE_DIR}/04-final-plan.txt"
plan_rc=${PIPESTATUS[0]}
set -e

[[ "${plan_rc}" -eq 0 ]] \
  || die "Final Terraform plan is not clean. Expected exit code 0; got ${plan_rc}."

grep -q "No changes" "${PHASE_DIR}/04-final-plan.txt" \
  || die "Final plan did not contain the expected 'No changes' message."

echo "FINAL_PLAN=NO_CHANGES" | tee "${PHASE_DIR}/05-final-plan-status.txt"

info "Capturing Terraform state, outputs, and providers"
terraform -chdir="${TF_ENV_DIR}" providers \
  2>&1 | tee "${PHASE_DIR}/06-terraform-providers.txt"

terraform -chdir="${TF_ENV_DIR}" state list \
  2>&1 | tee "${PHASE_DIR}/07-terraform-state-list.txt"

terraform -chdir="${TF_ENV_DIR}" output -json \
  2>&1 | tee "${PHASE_DIR}/08-terraform-outputs.json"

terraform -chdir="${TF_ENV_DIR}" show -json \
  > "${PHASE_DIR}/09-terraform-state.json"

info "Validating API, IAM, impersonation, and bucket inventory"
run_logged "${PHASE_DIR}/10-storage-api.json" \
  gcloud services list \
  --project="${PROJECT_ID}" \
  --enabled \
  --filter="config.name=storage.googleapis.com" \
  --format=json

gcloud auth print-access-token "${impersonation_args[@]}" >/dev/null
echo "Impersonation OK: ${TERRAFORM_DEPLOYER}" \
  | tee "${PHASE_DIR}/11-impersonation.txt"

run_logged "${PHASE_DIR}/12-terraform-deployer-iam.json" \
  gcloud projects get-iam-policy "${PROJECT_ID}" \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:${TERRAFORM_DEPLOYER}" \
  --format=json

run_logged "${PHASE_DIR}/13-gcloud-buckets.json" \
  gcloud storage buckets list \
  --project="${PROJECT_ID}" \
  "${impersonation_args[@]}" \
  --format=json

bucket_name="$(
  jq -r '
    [
      to_entries[]
      | select(.value.value != null)
      | select(
          (.key | ascii_downcase | test("bucket.*name|name.*bucket|bucket_name"))
          or
          (.value.value | type == "string" and startswith("gs://"))
        )
      | .value.value
    ][0] // empty
  ' "${PHASE_DIR}/08-terraform-outputs.json" 2>/dev/null
)"

bucket_name="${bucket_name#gs://}"
bucket_name="${bucket_name%/}"

if [[ -z "${bucket_name}" ]]; then
  bucket_name="$(
    jq -r '
      [
        .values.root_module.resources[]?,
        .values.root_module.child_modules[]?.resources[]?
      ]
      | map(select(.type == "google_storage_bucket"))
      | .[0].values.name // empty
    ' "${PHASE_DIR}/09-terraform-state.json"
  )"
fi

[[ -n "${bucket_name}" ]] \
  || die "Could not determine the Cloud Storage bucket name from Terraform outputs or state."

echo "${bucket_name}" | tee "${PHASE_DIR}/14-bucket-name.txt"
echo "gs://${bucket_name}" | tee "${PHASE_DIR}/15-bucket-uri.txt"

run_logged "${PHASE_DIR}/16-gcloud-bucket-describe.json" \
  gcloud storage buckets describe "gs://${bucket_name}" \
  "${impersonation_args[@]}" \
  --format=json

info "Validating bucket through JSON API"
access_token="$(gcloud auth print-access-token "${impersonation_args[@]}")"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${access_token}" \
  "https://storage.googleapis.com/storage/v1/b/${bucket_name}" \
  | jq '.' \
  | tee "${PHASE_DIR}/17-storage-rest-api.json"

info "Recording Git evidence"
git -C "${PROJECT_ROOT}" status \
  | tee "${PHASE_DIR}/18-git-status.txt"

git -C "${PROJECT_ROOT}" diff \
  | tee "${PHASE_DIR}/19-git-diff.patch"

git -C "${PROJECT_ROOT}" diff --check \
  2>&1 | tee "${PHASE_DIR}/20-git-diff-check.txt"

git -C "${PROJECT_ROOT}" log -5 --oneline --decorate \
  | tee "${PHASE_DIR}/21-git-log.txt"

info "Generating checksums for certification evidence"
(
  cd "${PHASE_DIR}"
  find . -maxdepth 1 -type f ! -name 'SHA256SUMS' -print0 \
    | sort -z \
    | xargs -0 sha256sum
) | tee "${PHASE_DIR}/SHA256SUMS"

info "OS_08_10 final certification checks passed"
echo "Bucket URI: gs://${bucket_name}"
echo "Evidence: ${PHASE_DIR}"
