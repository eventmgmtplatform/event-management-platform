output "repository_id" {
  description = "Identificador del repositorio Artifact Registry."
  value       = google_artifact_registry_repository.this.repository_id
}

output "repository_name" {
  description = "Nombre completo del recurso Artifact Registry."
  value       = google_artifact_registry_repository.this.name
}

output "repository_location" {
  description = "Ubicación del repositorio Artifact Registry."
  value       = google_artifact_registry_repository.this.location
}

output "repository_format" {
  description = "Formato del repositorio Artifact Registry."
  value       = google_artifact_registry_repository.this.format
}

output "repository_mode" {
  description = "Modo del repositorio Artifact Registry."
  value       = google_artifact_registry_repository.this.mode
}

output "repository_uri" {
  description = "URI base del repositorio Docker."
  value       = local.repository_uri
}
