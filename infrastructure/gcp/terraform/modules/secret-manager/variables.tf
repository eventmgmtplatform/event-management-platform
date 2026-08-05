variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se administrarán los secretos."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id no puede estar vacío."
  }
}

variable "default_labels" {
  description = "Labels base aplicados a todos los secretos."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Definiciones de contenedores de secretos. El módulo no administra payloads ni versiones."

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
    condition     = length(var.secrets) > 0
    error_message = "Debe definirse al menos un secreto."
  }

  validation {
    condition = alltrue([
      for secret in values(var.secrets) :
      can(regex("^[A-Za-z0-9_-]{1,255}$", secret.secret_id))
    ])
    error_message = "secret_id debe usar letras, números, guiones o guiones bajos."
  }

  validation {
    condition = alltrue([
      for secret in values(var.secrets) :
      length(trimspace(secret.purpose)) > 0
    ])
    error_message = "Cada secreto debe definir un purpose no vacío."
  }

  validation {
    condition = alltrue([
      for secret in values(var.secrets) :
      contains(["AUTOMATIC", "USER_MANAGED"], upper(secret.replication_type))
    ])
    error_message = "replication_type debe ser AUTOMATIC o USER_MANAGED."
  }

  validation {
    condition = alltrue([
      for secret in values(var.secrets) :
      upper(secret.replication_type) == "AUTOMATIC" ||
      length(secret.replication_locations) > 0
    ])
    error_message = "Los secretos USER_MANAGED deben definir al menos una replication_location."
  }

  validation {
    condition = alltrue(flatten([
      for secret in values(var.secrets) : [
        for role in keys(secret.iam) :
        startswith(role, "roles/")
      ]
    ]))
    error_message = "Todos los roles IAM deben utilizar el formato roles/nombreDelRol."
  }

  validation {
    condition = alltrue(flatten([
      for secret in values(var.secrets) : [
        for members in values(secret.iam) : [
          for member in members :
          can(regex("^(serviceAccount|user|group|domain):.+$", member))
        ]
      ]
    ]))
    error_message = "Cada miembro IAM debe incluir un prefijo válido."
  }
}
