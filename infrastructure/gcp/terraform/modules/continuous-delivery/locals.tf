locals {
  service_account_member = "serviceAccount:${google_service_account.execution.email}"

  artifact_repository_path = "${var.region}-docker.pkg.dev/${var.project_id}/${var.artifact_repository_id}"

  normalized_services = {
    for service_name, service in var.services :
    service_name => {
      service_name     = service_name
      container_name   = service.container_name
      container_port   = service.container_port
      image_repository = "${local.artifact_repository_path}/${service_name}"

      health = {
        type       = service.health.type
        path       = service.health.path
        live_path  = try(service.health.live_path, null)
        ready_path = try(service.health.ready_path, null)
      }

      dependencies = sort(tolist(service.dependencies))
      secret_refs  = sort(tolist(service.secret_refs))
    }
  }

  release_contract = {
    schema_version = "1.0"
    platform       = var.platform_name
    environment    = var.environment

    artifact_registry = {
      project_id    = var.project_id
      region        = var.region
      repository_id = var.artifact_repository_id
      repository    = local.artifact_repository_path
    }

    services = local.normalized_services
  }
}
