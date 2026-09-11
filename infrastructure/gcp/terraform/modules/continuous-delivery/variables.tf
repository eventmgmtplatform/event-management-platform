variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se configura Continuous Delivery."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id))
    error_message = "project_id debe ser un identificador válido de proyecto de Google Cloud."
  }
}

variable "region" {
  description = "Región principal utilizada por Artifact Registry."
  type        = string

  validation {
    condition     = can(regex("^[a-z]+-[a-z0-9]+[0-9]$", var.region))
    error_message = "region debe tener un formato válido, por ejemplo us-central1."
  }
}

variable "artifact_repository_id" {
  description = "ID del repositorio Docker de Artifact Registry."
  type        = string

  validation {
    condition = (
      length(var.artifact_repository_id) >= 4 &&
      length(var.artifact_repository_id) <= 63 &&
      can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.artifact_repository_id))
    )
    error_message = "artifact_repository_id no es válido."
  }
}

variable "service_account_id" {
  description = "Account ID de la cuenta de servicio de Continuous Delivery."
  type        = string
  default     = "continuous-delivery-executor"

  validation {
    condition = (
      length(var.service_account_id) >= 6 &&
      length(var.service_account_id) <= 30 &&
      can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.service_account_id))
    )
    error_message = "service_account_id debe tener entre 6 y 30 caracteres."
  }
}

variable "service_account_display_name" {
  description = "Nombre visible de la cuenta de servicio."
  type        = string
  default     = "Continuous Delivery Executor"

  validation {
    condition     = trimspace(var.service_account_display_name) != ""
    error_message = "service_account_display_name no puede estar vacío."
  }
}

variable "service_account_description" {
  description = "Descripción de la cuenta de servicio."
  type        = string
  default     = "Executes validated release delivery operations for OPEN EVENT MANAGEMENT."
}

variable "service_account_deletion_policy" {
  description = "Política de eliminación para la cuenta de servicio de Continuous Delivery."
  type        = string
  default     = "PREVENT"

  validation {
    condition = contains(
      ["PREVENT", "DELETE"],
      var.service_account_deletion_policy
    )
    error_message = "service_account_deletion_policy debe ser PREVENT o DELETE."
  }
}

variable "platform_name" {
  description = "Nombre lógico de la plataforma."
  type        = string
  default     = "event-management"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.platform_name))
    error_message = "platform_name no es válido."
  }
}

variable "environment" {
  description = "Ambiente asociado al contrato de Continuous Delivery."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "environment debe ser dev, qa o prod."
  }
}

variable "services" {
  description = "Contrato declarativo de servicios desplegables."

  type = map(object({
    container_name = string
    container_port = number

    health = object({
      type       = string
      path       = string
      live_path  = optional(string)
      ready_path = optional(string)
    })

    dependencies = optional(set(string), [])
    secret_refs  = optional(set(string), [])
  }))

  validation {
    condition = alltrue([
      for service_name, service in var.services :
      can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", service_name)) &&
      can(regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$", service.container_name)) &&
      service.container_port >= 1 &&
      service.container_port <= 65535 &&
      contains(["functional", "smallrye"], service.health.type) &&
      startswith(service.health.path, "/")
    ])
    error_message = "Cada servicio debe declarar nombre, contenedor, puerto y health válidos."
  }

  validation {
    condition = alltrue([
      for service_name, service in var.services :
      service.health.type != "smallrye" || (
        try(startswith(service.health.live_path, "/"), false) &&
        try(startswith(service.health.ready_path, "/"), false)
      )
    ])
    error_message = "Los servicios smallrye deben declarar live_path y ready_path."
  }

  validation {
    condition = alltrue([
      for service_name, service in var.services :
      alltrue([
        for secret_ref in service.secret_refs :
        can(regex("^[a-z][a-z0-9_]*[a-z0-9]$", secret_ref))
      ])
    ])
    error_message = "Las referencias de secretos no son válidas."
  }
}
