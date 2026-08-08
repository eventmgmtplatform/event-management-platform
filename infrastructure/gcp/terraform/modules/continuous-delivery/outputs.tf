output "service_account_id" {
  description = "Identificador completo de la cuenta de servicio."
  value       = google_service_account.execution.id
}

output "service_account_name" {
  description = "Nombre canónico de la cuenta de servicio."
  value       = google_service_account.execution.name
}

output "service_account_email" {
  description = "Correo de la cuenta de servicio."
  value       = google_service_account.execution.email
}

output "service_account_member" {
  description = "Principal IAM de la cuenta de servicio."
  value       = local.service_account_member
}

output "artifact_registry_reader_binding_id" {
  description = "Identificador del binding Artifact Registry Reader."
  value       = google_artifact_registry_repository_iam_member.reader.id
}

output "artifact_repository_path" {
  description = "Ruta base del repositorio Docker."
  value       = local.artifact_repository_path
}

output "services" {
  description = "Contratos normalizados de los servicios."
  value       = local.normalized_services
}

output "release_contract" {
  description = "Contrato declarativo de Continuous Delivery."
  value       = local.release_contract
}
