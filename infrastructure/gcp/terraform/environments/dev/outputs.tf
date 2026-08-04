output "project_id" {
  description = "ID del proyecto configurado para el ambiente."
  value       = var.project_id
}

output "project_number" {
  description = "Número del proyecto configurado para el ambiente."
  value       = var.project_number
}

output "environment" {
  description = "Ambiente administrado por esta configuración."
  value       = var.environment
}

output "region" {
  description = "Región principal configurada."
  value       = var.region
}

output "zone" {
  description = "Zona principal configurada."
  value       = var.zone
}

output "platform_name" {
  description = "Nombre lógico de la plataforma."
  value       = var.platform_name
}

output "terraform_deployer_member" {
  description = "Identificador IAM de terraform-deployer."
  value       = module.iam.terraform_deployer_member
}

output "terraform_deployer_project_roles" {
  description = "Roles de proyecto administrados para terraform-deployer."
  value       = module.iam.terraform_deployer_project_roles
}

output "terraform_deployer_role_bindings" {
  description = "Bindings IAM administrados para terraform-deployer."
  value       = module.iam.terraform_deployer_role_bindings
}

output "network_id" {
  description = "ID of the Event Management VPC."
  value       = module.networking.network_id
}

output "network_name" {
  description = "Name of the Event Management VPC."
  value       = module.networking.network_name
}

output "network_self_link" {
  description = "Self-link of the Event Management VPC."
  value       = module.networking.network_self_link
}

output "subnet_ids" {
  description = "Subnet IDs keyed by logical subnet name."
  value       = module.networking.subnet_ids
}

output "subnet_names" {
  description = "Subnet names keyed by logical subnet name."
  value       = module.networking.subnet_names
}

output "subnet_cidr_ranges" {
  description = "Subnet CIDR ranges keyed by logical subnet name."
  value       = module.networking.subnet_cidr_ranges
}
