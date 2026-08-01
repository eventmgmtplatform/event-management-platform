variable "organization" {
  description = "Organización o dominio lógico propietario de la plataforma."
  type        = string

  validation {
    condition = (
      length(trimspace(var.organization)) >= 2 &&
      length(trimspace(var.organization)) <= 63 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.organization))
    )
    error_message = "organization debe tener entre 2 y 63 caracteres y usar únicamente minúsculas, números y guiones simples."
  }
}

variable "platform" {
  description = "Nombre lógico completo de la plataforma."
  type        = string
  default     = "event-management"

  validation {
    condition = (
      length(trimspace(var.platform)) >= 2 &&
      length(trimspace(var.platform)) <= 63 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.platform))
    )
    error_message = "platform debe tener entre 2 y 63 caracteres y usar únicamente minúsculas, números y guiones simples."
  }
}

variable "platform_short" {
  description = "Nombre corto utilizado en recursos con límites restrictivos."
  type        = string
  default     = "em"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,7}$", var.platform_short))
    error_message = "platform_short debe comenzar con una letra, usar solo minúsculas o números y tener entre 2 y 8 caracteres."
  }
}

variable "cloud" {
  description = "Proveedor cloud objetivo."
  type        = string

  validation {
    condition     = contains(["gcp", "aws", "azure"], var.cloud)
    error_message = "cloud debe ser gcp, aws o azure."
  }
}

variable "environment" {
  description = "Ambiente de despliegue."
  type        = string

  validation {
    condition = contains(
      ["dev", "test", "qa", "stage", "prod", "sandbox"],
      var.environment
    )
    error_message = "environment debe ser dev, test, qa, stage, prod o sandbox."
  }
}

variable "customer" {
  description = "Cliente, tenant o dominio compartido."
  type        = string
  default     = "shared"

  validation {
    condition = (
      length(trimspace(var.customer)) >= 1 &&
      length(trimspace(var.customer)) <= 20 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.customer))
    )
    error_message = "customer debe tener entre 1 y 20 caracteres y usar únicamente minúsculas, números y guiones simples."
  }
}

variable "region" {
  description = "Región lógica independiente del identificador físico del proveedor."
  type        = string
  default     = "northamerica"

  validation {
    condition = (
      length(trimspace(var.region)) >= 2 &&
      length(trimspace(var.region)) <= 32 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.region))
    )
    error_message = "region debe usar únicamente minúsculas, números y guiones simples."
  }
}

variable "owner" {
  description = "Equipo responsable de los recursos."
  type        = string

  validation {
    condition = (
      length(trimspace(var.owner)) >= 2 &&
      length(trimspace(var.owner)) <= 63 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.owner))
    )
    error_message = "owner debe usar únicamente minúsculas, números y guiones simples."
  }
}

variable "managed_by" {
  description = "Herramienta responsable de administrar los recursos."
  type        = string
  default     = "terraform"

  validation {
    condition     = can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.managed_by))
    error_message = "managed_by debe usar únicamente minúsculas, números y guiones simples."
  }
}

variable "repository" {
  description = "Repositorio que contiene la infraestructura."
  type        = string
  default     = "event-management-platform"

  validation {
    condition = (
      length(trimspace(var.repository)) >= 2 &&
      length(trimspace(var.repository)) <= 63 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.repository))
    )
    error_message = "repository debe usar únicamente minúsculas, números y guiones simples."
  }
}

variable "cost_center" {
  description = "Centro de costos asociado."
  type        = string
  default     = "transformation"

  validation {
    condition = (
      length(trimspace(var.cost_center)) >= 2 &&
      length(trimspace(var.cost_center)) <= 63 &&
      can(regex("^[a-z0-9]+(?:-[a-z0-9]+)*$", var.cost_center))
    )
    error_message = "cost_center debe usar únicamente minúsculas, números y guiones simples."
  }
}

variable "data_classification" {
  description = "Clasificación general de la información."
  type        = string
  default     = "internal"

  validation {
    condition = contains(
      ["public", "internal", "confidential", "restricted"],
      var.data_classification
    )
    error_message = "data_classification debe ser public, internal, confidential o restricted."
  }
}

variable "criticality" {
  description = "Criticidad operativa general."
  type        = string
  default     = "medium"

  validation {
    condition     = contains(["low", "medium", "high", "critical"], var.criticality)
    error_message = "criticality debe ser low, medium, high o critical."
  }
}

variable "additional_labels" {
  description = "Labels adicionales. Los labels comunes obligatorios tienen prioridad."
  type        = map(string)
  default     = {}

  validation {
    condition = alltrue([
      for key, value in var.additional_labels :
      can(regex("^[a-z][a-z0-9_-]{0,62}$", key)) &&
      can(regex("^[a-z0-9_-]{0,63}$", value))
    ])
    error_message = "additional_labels debe utilizar claves y valores compatibles con labels de GCP."
  }
}

variable "additional_tags" {
  description = "Tags adicionales para proveedores que soportan etiquetas con formato libre."
  type        = map(string)
  default     = {}
}
