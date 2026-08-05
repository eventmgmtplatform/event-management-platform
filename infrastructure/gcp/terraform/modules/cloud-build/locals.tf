locals {
  execution_member = "serviceAccount:${google_service_account.execution.email}"

  common_annotations = {
    application = var.platform_name
    environment = var.environment
    managed_by  = "terraform"
    module      = "cloud-build"
  }

  repository_connection_enabled = var.repository_connection.enabled

  secret_access_bindings = {
    for secret_id in var.secret_access :
    secret_id => {
      secret_id = secret_id
      role      = "roles/secretmanager.secretAccessor"
    }
  }
}
