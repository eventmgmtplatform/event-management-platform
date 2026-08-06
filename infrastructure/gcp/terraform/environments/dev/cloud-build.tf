module "cloud_build" {
  source = "../../modules/cloud-build"

  project_id    = var.project_id
  location      = var.region
  environment   = var.environment
  platform_name = var.platform_name

  execution_service_account = var.cloud_build_execution_service_account
  execution_project_roles   = var.cloud_build_execution_project_roles

  artifact_registry = {
    repository_id = module.artifact_registry.repository_id
    location      = module.artifact_registry.repository_location
    writer_role   = "roles/artifactregistry.writer"
  }

  source_bucket = var.cloud_build_source_bucket
  secret_access = var.cloud_build_secret_access

  repository_connection = var.cloud_build_repository_connection
  repositories          = var.cloud_build_repositories
  triggers              = var.cloud_build_triggers

  depends_on = [
    module.iam,
    module.artifact_registry,
    module.secret_manager
  ]
}
