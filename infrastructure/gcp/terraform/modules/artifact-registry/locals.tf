locals {
  repository_uri = var.format == "DOCKER" ? (
    "${var.location}-docker.pkg.dev/${var.project_id}/${var.repository_id}"
  ) : null
}
