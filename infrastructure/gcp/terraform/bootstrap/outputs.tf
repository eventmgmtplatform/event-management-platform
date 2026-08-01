output "state_bucket_name" {
  description = "Nombre del bucket que almacenará los estados Terraform."
  value       = google_storage_bucket.terraform_state.name
}

output "state_bucket_url" {
  description = "URI del bucket de estados Terraform."
  value       = google_storage_bucket.terraform_state.url
}

output "terraform_service_account_email" {
  description = "Correo de la cuenta de servicio utilizada por Terraform."
  value       = google_service_account.terraform_deployer.email
}

output "enabled_services" {
  description = "APIs administradas por el bootstrap."
  value       = sort(keys(google_project_service.required_apis))
}

output "terraform_operator_email" {
  description = "Usuario autorizado para suplantar Terraform Deployer."
  value       = var.terraform_operator_email
}
