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

output "artifact_registry_repository_id" {
  description = "Identificador del repositorio Artifact Registry."
  value       = module.artifact_registry.repository_id
}

output "artifact_registry_repository_name" {
  description = "Nombre completo del repositorio Artifact Registry."
  value       = module.artifact_registry.repository_name
}

output "artifact_registry_repository_location" {
  description = "Ubicación del repositorio Artifact Registry."
  value       = module.artifact_registry.repository_location
}

output "artifact_registry_repository_format" {
  description = "Formato del repositorio Artifact Registry."
  value       = module.artifact_registry.repository_format
}

output "artifact_registry_repository_uri" {
  description = "URI base del repositorio Docker."
  value       = module.artifact_registry.repository_uri
}

output "cloud_storage_bucket_ids" {
  description = "IDs de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_ids
}

output "cloud_storage_bucket_names" {
  description = "Nombres de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_names
}

output "cloud_storage_bucket_urls" {
  description = "URLs gs:// de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_urls
}

output "cloud_storage_bucket_self_links" {
  description = "Self-links de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_self_links
}

output "cloud_storage_bucket_locations" {
  description = "Ubicaciones de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_locations
}

output "cloud_storage_bucket_storage_classes" {
  description = "Clases de almacenamiento de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_storage_classes
}

output "cloud_storage_bucket_labels" {
  description = "Labels efectivos de los buckets funcionales Cloud Storage por clave lógica."
  value       = module.cloud_storage.bucket_labels
}


# OS_08_11_SECRET_MANAGER_OUTPUTS
output "secret_manager_secret_ids" {
  description = "IDs de Secret Manager por clave lógica."
  value       = module.secret_manager.secret_ids
}

output "secret_manager_secret_names" {
  description = "Nombres completos de Secret Manager por clave lógica."
  value       = module.secret_manager.secret_names
}

output "secret_manager_secret_labels" {
  description = "Labels efectivos de los secretos."
  value       = module.secret_manager.secret_labels
}

output "secret_manager_replication_types" {
  description = "Tipos de replicación de los secretos."
  value       = module.secret_manager.replication_types
}

output "secret_manager_replication_locations" {
  description = "Ubicaciones configuradas para replicación USER_MANAGED."
  value       = module.secret_manager.replication_locations
}

output "secret_manager_iam_member_bindings" {
  description = "Bindings IAM administrados a nivel de secreto."
  value       = module.secret_manager.iam_member_bindings
}
# END_OS_08_11_SECRET_MANAGER_OUTPUTS


# OS_08_12_CLOUD_BUILD_OUTPUTS
output "cloud_build_execution_service_account_email" {
  description = "Correo de la cuenta ejecutora Cloud Build."
  value       = module.cloud_build.execution_service_account_email
}

output "cloud_build_execution_service_account_name" {
  description = "Nombre completo de la cuenta ejecutora Cloud Build."
  value       = module.cloud_build.execution_service_account_name
}

output "cloud_build_execution_project_roles" {
  description = "Roles de proyecto de la cuenta ejecutora."
  value       = module.cloud_build.execution_project_roles
}

output "cloud_build_artifact_registry_writer_binding" {
  description = "Binding Artifact Registry Writer de Cloud Build."
  value       = module.cloud_build.artifact_registry_writer_binding
}

output "cloud_build_secret_accessor_bindings" {
  description = "Bindings Secret Manager de Cloud Build."
  value       = module.cloud_build.secret_accessor_bindings
}

output "cloud_build_connection_id" {
  description = "ID de conexión GitHub Cloud Build v2."
  value       = module.cloud_build.connection_id
}

output "cloud_build_repository_ids" {
  description = "IDs de repositorios Cloud Build v2."
  value       = module.cloud_build.repository_ids
}

output "cloud_build_trigger_ids" {
  description = "IDs de triggers Cloud Build."
  value       = module.cloud_build.trigger_ids
}
# END_OS_08_12_CLOUD_BUILD_OUTPUTS


# OS_08_08_4_CLOUD_BUILD_SERVICE_AGENT_OUTPUT
output "iam_service_agent_role_bindings" {
  description = "Project IAM bindings assigned to Google-managed service agents."
  value       = module.iam.service_agent_role_bindings
}
# END_OS_08_08_4_CLOUD_BUILD_SERVICE_AGENT_OUTPUT
