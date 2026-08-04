variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se creará Artifact Registry."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "La variable project_id no puede estar vacía."
  }
}

variable "location" {
  description = "Ubicación regional del repositorio Artifact Registry."
  type        = string

  validation {
    condition     = length(trimspace(var.location)) > 0
    error_message = "La variable location no puede estar vacía."
  }
}

variable "repository_id" {
  description = "Identificador único del repositorio Artifact Registry."
  type        = string

  validation {
    condition = can(regex(
      "^[a-z][a-z0-9-]{2,61}[a-z0-9]$",
      var.repository_id
    ))
    error_message = "repository_id debe usar minúsculas, números y guiones, comenzar con letra y terminar con letra o número."
  }
}

variable "description" {
  description = "Descripción del repositorio Artifact Registry."
  type        = string
  default     = null
}

variable "format" {
  description = "Formato del repositorio Artifact Registry."
  type        = string
  default     = "DOCKER"

  validation {
    condition = contains([
      "DOCKER",
      "MAVEN",
      "NPM",
      "PYTHON",
      "APT",
      "YUM",
      "GO",
      "GENERIC",
      "KFP"
    ], var.format)

    error_message = "El formato configurado no está permitido por este módulo."
  }
}

variable "mode" {
  description = "Modo del repositorio Artifact Registry."
  type        = string
  default     = "STANDARD_REPOSITORY"

  validation {
    condition = contains([
      "STANDARD_REPOSITORY",
      "REMOTE_REPOSITORY",
      "VIRTUAL_REPOSITORY"
    ], var.mode)

    error_message = "mode debe ser STANDARD_REPOSITORY, REMOTE_REPOSITORY o VIRTUAL_REPOSITORY."
  }
}

variable "immutable_tags" {
  description = "Impide sobrescribir etiquetas Docker existentes cuando está habilitado."
  type        = bool
  default     = false
}

variable "labels" {
  description = "Etiquetas aplicadas al repositorio Artifact Registry."
  type        = map(string)
  default     = {}
}
