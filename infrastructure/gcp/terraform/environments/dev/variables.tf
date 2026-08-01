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
