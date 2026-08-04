module "artifact_registry" {
  source = "../../modules/artifact-registry"

  project_id    = var.project_id
  location      = var.artifact_registry_location
  repository_id = var.artifact_registry_repository_id
  description   = var.artifact_registry_description

  format         = "DOCKER"
  mode           = "STANDARD_REPOSITORY"
  immutable_tags = var.artifact_registry_immutable_tags

  labels = {
    application = var.platform_name
    environment = var.environment
    managed_by  = "terraform"
    purpose     = "container-images"
  }
}
