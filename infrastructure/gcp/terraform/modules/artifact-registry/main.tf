resource "google_artifact_registry_repository" "this" {
  project       = var.project_id
  location      = var.location
  repository_id = var.repository_id
  description   = var.description
  format        = var.format
  mode          = var.mode
  labels        = var.labels

  dynamic "docker_config" {
    for_each = var.format == "DOCKER" ? [1] : []

    content {
      immutable_tags = var.immutable_tags
    }
  }
}
