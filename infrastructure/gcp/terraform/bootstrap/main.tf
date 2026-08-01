resource "google_project_service" "required_apis" {
  for_each = toset([
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "serviceusage.googleapis.com",
    "storage.googleapis.com"
  ])

  project = var.project_id
  service = each.value

  disable_on_destroy = false
}

resource "google_storage_bucket" "terraform_state" {
  name     = var.state_bucket_name
  project  = var.project_id
  location = var.region

  storage_class = "STANDARD"

  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = true
  }

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  lifecycle {
    prevent_destroy = true
  }

  labels = {
    application = "event-management"
    environment = var.environment
    managed_by  = "terraform"
    purpose     = "terraform-state"
  }

  depends_on = [
    google_project_service.required_apis
  ]
}

resource "google_service_account" "terraform_deployer" {
  project      = var.project_id
  account_id   = var.terraform_service_account_id
  display_name = "Terraform Deployer"
  description  = "Cuenta de servicio para administrar la infraestructura de Event Management."

  depends_on = [
    google_project_service.required_apis
  ]
}

resource "google_storage_bucket_iam_member" "terraform_state_admin" {
  bucket = google_storage_bucket.terraform_state.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.terraform_deployer.email}"
}
