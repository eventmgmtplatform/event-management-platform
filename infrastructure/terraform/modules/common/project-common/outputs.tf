output "context" {
  description = "Contexto completo y normalizado del despliegue."
  value       = local.context
}

output "organization" {
  description = "Organización normalizada."
  value       = local.normalized.organization
}

output "cloud" {
  description = "Proveedor cloud normalizado."
  value       = local.normalized.cloud
}

output "environment" {
  description = "Ambiente normalizado."
  value       = local.normalized.environment
}

output "customer" {
  description = "Cliente o tenant normalizado."
  value       = local.normalized.customer
}

output "region" {
  description = "Región lógica normalizada."
  value       = local.normalized.region
}

output "name_prefix" {
  description = "Prefijo estándar para nombres de recursos."
  value       = local.name_prefix
}

output "name_prefix_short" {
  description = "Prefijo corto para recursos con límites restrictivos."
  value       = local.name_prefix_short
}

output "name_prefix_compact" {
  description = "Prefijo sin separadores para recursos que no aceptan guiones."
  value       = local.name_prefix_compact
}

output "common_labels" {
  description = "Labels comunes compatibles con las convenciones de GCP."
  value       = local.common_labels
}

output "common_tags" {
  description = "Tags comunes para AWS, Azure u otros consumidores."
  value       = local.common_tags
}

output "resource_names" {
  description = "Nombres sugeridos para componentes compartidos."
  value       = local.resource_names
}

output "module_metadata" {
  description = "Identificación y versión del módulo."
  value = {
    name       = local.module_name
    version    = local.module_version
    managed_by = local.normalized.managed_by
  }
}
