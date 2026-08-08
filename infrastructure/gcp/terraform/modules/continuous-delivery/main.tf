resource "google_service_account" "execution" {
  project         = var.project_id
  account_id      = var.service_account_id
  display_name    = var.service_account_display_name
  description     = var.service_account_description
  deletion_policy = var.service_account_deletion_policy
}

resource "google_artifact_registry_repository_iam_member" "reader" {
  project    = var.project_id
  location   = var.region
  repository = var.artifact_repository_id
  role       = "roles/artifactregistry.reader"
  member     = local.service_account_member
}
