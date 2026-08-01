variable "project_id" {
  description = "ID del proyecto de Google Cloud."
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

variable "region" {
  description = "Región principal de Google Cloud."
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Ambiente inicial administrado."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "El ambiente debe ser dev, qa o prod."
  }
}

variable "state_bucket_name" {
  description = "Nombre globalmente único del bucket de estados Terraform."
  type        = string

  validation {
    condition = (
      length(var.state_bucket_name) >= 3 &&
      length(var.state_bucket_name) <= 63 &&
      can(regex("^[a-z0-9][a-z0-9._-]*[a-z0-9]$", var.state_bucket_name))
    )
    error_message = "El nombre del bucket debe cumplir las reglas de Cloud Storage."
  }
}

variable "terraform_service_account_id" {
  description = "ID de la cuenta de servicio utilizada por Terraform."
  type        = string
  default     = "terraform-deployer"
}
