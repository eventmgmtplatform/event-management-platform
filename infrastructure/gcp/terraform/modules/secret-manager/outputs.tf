output "secret_ids" {
  description = "IDs de los secretos por clave lógica."
  value = {
    for key, secret in google_secret_manager_secret.this :
    key => secret.secret_id
  }
}

output "secret_names" {
  description = "Nombres completos de los secretos por clave lógica."
  value = {
    for key, secret in google_secret_manager_secret.this :
    key => secret.name
  }
}

output "secret_labels" {
  description = "Labels efectivos de los secretos por clave lógica."
  value = {
    for key, secret in google_secret_manager_secret.this :
    key => secret.labels
  }
}

output "replication_types" {
  description = "Tipo de replicación configurado por secreto."
  value = {
    for key, secret in local.normalized_secrets :
    key => secret.replication_type
  }
}

output "replication_locations" {
  description = "Ubicaciones configuradas para replicación USER_MANAGED."
  value = {
    for key, secret in local.normalized_secrets :
    key => secret.replication_locations
  }
}

output "iam_member_bindings" {
  description = "Bindings IAM no autoritativos administrados por el módulo."
  value = {
    for key, binding in google_secret_manager_secret_iam_member.this :
    key => binding.id
  }
}
