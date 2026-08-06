variable "project_id" {
  description = "ID del proyecto de Google Cloud."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id no puede estar vacío."
  }
}

variable "location" {
  description = "Ubicación regional de los recursos Cloud Build."
  type        = string

  validation {
    condition     = length(trimspace(var.location)) > 0
    error_message = "location no puede estar vacía."
  }
}

variable "environment" {
  description = "Ambiente lógico de despliegue."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "environment debe ser dev, qa o prod."
  }
}

variable "platform_name" {
  description = "Nombre lógico de la plataforma."
  type        = string

  validation {
    condition     = length(trimspace(var.platform_name)) > 0
    error_message = "platform_name no puede estar vacío."
  }
}

variable "execution_service_account" {
  description = "Definición de la cuenta dedicada que ejecutará builds."

  type = object({
    account_id      = string
    display_name    = string
    description     = string
    deletion_policy = optional(string, "PREVENT")
  })

  validation {
    condition = can(regex(
      "^[a-z]([-a-z0-9]*[a-z0-9])$",
      var.execution_service_account.account_id
    ))
    error_message = "account_id debe cumplir el formato RFC1035 para cuentas de servicio."
  }

  validation {
    condition = contains(
      ["DELETE", "PREVENT"],
      upper(var.execution_service_account.deletion_policy)
    )
    error_message = "deletion_policy debe ser DELETE o PREVENT."
  }
}

variable "execution_project_roles" {
  description = "Roles de proyecto concedidos a la cuenta ejecutora."
  type        = set(string)
  default     = ["roles/logging.logWriter"]

  validation {
    condition = alltrue([
      for role in var.execution_project_roles :
      startswith(role, "roles/")
    ])
    error_message = "Todos los roles deben usar el formato roles/nombre."
  }

  validation {
    condition = length(setintersection(
      var.execution_project_roles,
      toset([
        "roles/owner",
        "roles/editor",
        "roles/resourcemanager.projectIamAdmin",
        "roles/iam.serviceAccountAdmin"
      ])
    )) == 0
    error_message = "La cuenta ejecutora no puede recibir roles administrativos generales."
  }
}

variable "artifact_registry" {
  description = "Repositorio Artifact Registry donde Cloud Build publicará imágenes."

  type = object({
    repository_id = string
    location      = string
    writer_role   = optional(string, "roles/artifactregistry.writer")
  })

  validation {
    condition     = length(trimspace(var.artifact_registry.repository_id)) > 0
    error_message = "artifact_registry.repository_id no puede estar vacío."
  }

  validation {
    condition = (
      var.artifact_registry.writer_role ==
      "roles/artifactregistry.writer"
    )
    error_message = "El rol admitido para publicación es roles/artifactregistry.writer."
  }
}

variable "secret_access" {
  description = "Acceso opcional por secreto para la cuenta ejecutora."
  type        = set(string)
  default     = []
}

variable "repository_connection" {
  description = "Configuración opcional de conexión GitHub Cloud Build v2."

  type = object({
    enabled              = optional(bool, false)
    name                 = optional(string)
    app_installation_id  = optional(number)
    oauth_secret_version = optional(string)
  })

  default = {
    enabled = false
  }

  validation {
    condition = (
      !var.repository_connection.enabled ||
      (
        try(length(trimspace(var.repository_connection.name)) > 0, false) &&
        try(var.repository_connection.app_installation_id > 0, false) &&
        try(length(trimspace(var.repository_connection.oauth_secret_version)) > 0, false)
      )
    )
    error_message = "Una conexión habilitada requiere name, app_installation_id y oauth_secret_version."
  }
}

variable "repositories" {
  description = "Repositorios enlazados a la conexión Cloud Build v2."

  type = map(object({
    name       = string
    remote_uri = string
  }))

  default = {}
}

variable "triggers" {
  description = "Triggers Cloud Build asociados a repositorios v2."

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

  validation {
    condition = alltrue([
      for trigger in values(var.triggers) :
      contains(["PUSH_BRANCH", "PUSH_TAG", "PULL_REQUEST"], upper(trigger.event_type))
    ])
    error_message = "event_type debe ser PUSH_BRANCH, PUSH_TAG o PULL_REQUEST."
  }
}

variable "source_bucket" {
  description = "Bucket opcional que almacena archivos fuente para builds manuales."

  type = object({
    name        = string
    reader_role = optional(string, "roles/storage.objectViewer")
  })

  default = {
    name = ""
  }

  validation {
    condition = (
      length(trimspace(var.source_bucket.name)) == 0 ||
      can(regex(
        "^[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]$",
        var.source_bucket.name
      ))
    )
    error_message = "source_bucket.name debe estar vacío o contener un nombre válido de bucket."
  }

  validation {
    condition = (
      length(trimspace(var.source_bucket.name)) == 0 ||
      var.source_bucket.reader_role == "roles/storage.objectViewer"
    )
    error_message = "El único rol admitido para source_bucket es roles/storage.objectViewer."
  }
}
