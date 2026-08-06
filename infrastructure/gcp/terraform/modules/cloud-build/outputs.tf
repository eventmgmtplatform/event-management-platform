output "execution_service_account_email" {
  description = "Correo de la cuenta dedicada para ejecutar builds."
  value       = google_service_account.execution.email
}

output "execution_service_account_name" {
  description = "Nombre completo de la cuenta ejecutora."
  value       = google_service_account.execution.name
}

output "execution_service_account_member" {
  description = "Identificador IAM de la cuenta ejecutora."
  value       = local.execution_member
}

output "execution_project_roles" {
  description = "Roles de proyecto concedidos a la cuenta ejecutora."
  value       = sort(tolist(var.execution_project_roles))
}

output "artifact_registry_writer_binding" {
  description = "Binding de escritura en Artifact Registry."
  value       = google_artifact_registry_repository_iam_member.execution_writer.id
}

output "secret_accessor_bindings" {
  description = "Bindings por secreto concedidos a la cuenta ejecutora."
  value = {
    for secret_id, binding in google_secret_manager_secret_iam_member.execution_accessor :
    secret_id => binding.id
  }
}

output "connection_id" {
  description = "ID de la conexión Cloud Build v2, cuando está habilitada."
  value       = try(google_cloudbuildv2_connection.github[0].id, null)
}

output "repository_ids" {
  description = "IDs de repositorios Cloud Build v2."
  value = {
    for key, repository in google_cloudbuildv2_repository.this :
    key => repository.id
  }
}

output "trigger_ids" {
  description = "IDs de triggers Cloud Build."
  value = {
    for key, trigger in google_cloudbuild_trigger.this :
    key => trigger.trigger_id
  }
}

output "source_bucket_reader_binding" {
  description = "Binding de lectura sobre el bucket fuente de Cloud Build."
  value       = try(google_storage_bucket_iam_member.source_reader[0].id, null)
}
