variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se desplegará la plataforma."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "La variable project_id no puede estar vacía."
  }
}

variable "project_number" {
  description = "Número único del proyecto de Google Cloud."
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.project_number))
    error_message = "La variable project_number debe contener únicamente números."
  }
}

variable "environment" {
  description = "Ambiente de despliegue de la plataforma."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "El ambiente debe ser dev, qa o prod."
  }
}

variable "region" {
  description = "Región principal de Google Cloud."
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona principal de Google Cloud."
  type        = string
  default     = "us-central1-a"
}

variable "platform_name" {
  description = "Nombre lógico de la plataforma."
  type        = string
  default     = "event-management"
}

variable "terraform_deployer_email" {
  description = "Correo de la cuenta de servicio utilizada para desplegar infraestructura con Terraform."
  type        = string

  validation {
    condition = can(regex(
      "^[a-z0-9][a-z0-9-]{4,28}[a-z0-9]@[a-z][a-z0-9-]{4,28}[a-z0-9]\\.iam\\.gserviceaccount\\.com$",
      var.terraform_deployer_email
    ))
    error_message = "terraform_deployer_email debe ser un correo válido de cuenta de servicio de Google Cloud."
  }
}

variable "terraform_deployer_project_roles" {
  description = "Roles de proyecto asignados mediante Terraform a terraform-deployer."
  type        = set(string)

  validation {
    condition = alltrue([
      for role in var.terraform_deployer_project_roles :
      startswith(role, "roles/")
    ])
    error_message = "Todos los roles deben utilizar el formato roles/nombreDelRol."
  }

  validation {
    condition = length(setintersection(
      var.terraform_deployer_project_roles,
      toset([
        "roles/owner",
        "roles/editor"
      ])
    )) == 0
    error_message = "No está permitido asignar roles/owner ni roles/editor a terraform-deployer."
  }
}


variable "network_name" {
  description = "Name of the Event Management custom VPC."
  type        = string
}

variable "network_routing_mode" {
  description = "Dynamic routing mode for the Event Management VPC."
  type        = string
  default     = "REGIONAL"

  validation {
    condition     = contains(["REGIONAL", "GLOBAL"], var.network_routing_mode)
    error_message = "The network routing mode must be REGIONAL or GLOBAL."
  }
}

variable "network_subnets" {
  description = "Subnet definitions for the Event Management VPC."

  type = map(object({
    name                  = string
    ip_cidr_range         = string
    region                = string
    private_google_access = optional(bool, true)
    description           = optional(string)
    stack_type            = optional(string, "IPV4_ONLY")
  }))

  validation {
    condition     = length(var.network_subnets) > 0
    error_message = "At least one network subnet must be defined."
  }
}

variable "enable_internal_firewall" {
  description = "Enable the internal Event Management firewall rule."
  type        = bool
  default     = true
}

variable "enable_management_ssh" {
  description = "Enable SSH from the management subnet to tagged instances."
  type        = bool
  default     = true
}

variable "artifact_registry_location" {
  description = "Ubicación regional del repositorio Artifact Registry."
  type        = string
  default     = "us-central1"
}

variable "artifact_registry_repository_id" {
  description = "Identificador del repositorio Docker Artifact Registry."
  type        = string

  validation {
    condition = can(regex(
      "^[a-z][a-z0-9-]{2,61}[a-z0-9]$",
      var.artifact_registry_repository_id
    ))
    error_message = "artifact_registry_repository_id debe usar minúsculas, números y guiones."
  }
}

variable "artifact_registry_description" {
  description = "Descripción del repositorio Docker."
  type        = string
  default     = "Container images for Event Management."
}

variable "artifact_registry_immutable_tags" {
  description = "Controla si las etiquetas Docker son inmutables."
  type        = bool
  default     = false
}

variable "cloud_storage_buckets" {
  description = "Definiciones de buckets funcionales Cloud Storage para el ambiente DEV."

  type = map(object({
    name                          = string
    purpose                       = string
    location                      = optional(string)
    storage_class                 = optional(string, "STANDARD")
    force_destroy                 = optional(bool, false)
    uniform_bucket_level_access   = optional(bool, true)
    public_access_prevention      = optional(string, "enforced")
    versioning_enabled            = optional(bool, true)
    soft_delete_retention_seconds = optional(number, 604800)
    labels                        = optional(map(string), {})
  }))

  validation {
    condition     = length(var.cloud_storage_buckets) > 0
    error_message = "Debe definirse al menos un bucket funcional para Cloud Storage."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.cloud_storage_buckets) :
      can(regex(
        "^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$",
        bucket.name
      ))
    ])

    error_message = "Los nombres de bucket deben contener entre 3 y 63 caracteres y usar minúsculas, números, puntos, guiones o guiones bajos."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.cloud_storage_buckets) :
      length(trimspace(bucket.purpose)) > 0
    ])

    error_message = "Cada bucket debe definir un purpose no vacío."
  }
}


# OS_08_11_SECRET_MANAGER_VARIABLES
variable "secret_manager_secrets" {
  description = "Definiciones de contenedores de secretos administrados para Event Management."

  type = map(object({
    secret_id             = string
    purpose               = string
    replication_type      = optional(string, "AUTOMATIC")
    replication_locations = optional(set(string), [])
    labels                = optional(map(string), {})
    deletion_protection   = optional(bool, true)
    iam                   = optional(map(set(string)), {})
  }))

  validation {
    condition     = length(var.secret_manager_secrets) > 0
    error_message = "Debe definirse al menos un secreto de Secret Manager."
  }

  validation {
    condition = alltrue([
      for secret in values(var.secret_manager_secrets) :
      contains(["AUTOMATIC", "USER_MANAGED"], upper(secret.replication_type))
    ])
    error_message = "replication_type debe ser AUTOMATIC o USER_MANAGED."
  }
}
# END_OS_08_11_SECRET_MANAGER_VARIABLES


# OS_08_12_CLOUD_BUILD_VARIABLES
variable "cloud_build_execution_service_account" {
  description = "Cuenta dedicada para ejecutar builds Cloud Build."

  type = object({
    account_id      = string
    display_name    = string
    description     = string
    deletion_policy = optional(string, "PREVENT")
  })
}

variable "cloud_build_execution_project_roles" {
  description = "Roles de proyecto para la cuenta ejecutora Cloud Build."
  type        = set(string)
  default     = ["roles/logging.logWriter"]
}

variable "cloud_build_secret_access" {
  description = "IDs de secretos accesibles por la cuenta ejecutora."
  type        = set(string)
  default     = []
}

variable "cloud_build_repository_connection" {
  description = "Conexión opcional GitHub Cloud Build v2."

  type = object({
    enabled              = optional(bool, false)
    name                 = optional(string)
    app_installation_id  = optional(number)
    oauth_secret_version = optional(string)
  })

  default = {
    enabled = false
  }
}

variable "cloud_build_repositories" {
  description = "Repositorios Cloud Build v2."

  type = map(object({
    name       = string
    remote_uri = string
  }))

  default = {}
}

variable "cloud_build_triggers" {
  description = "Triggers Cloud Build."

  type = map(object({
    name              = string
    repository_key    = string
    filename          = string
    event_type        = string
    branch_regex      = optional(string)
    tag_regex         = optional(string)
    included_files    = optional(list(string), [])
    ignored_files     = optional(list(string), [])
    substitutions     = optional(map(string), {})
    disabled          = optional(bool, false)
    approval_required = optional(bool, false)
  }))

  default = {}
}
# END_OS_08_12_CLOUD_BUILD_VARIABLES
