variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se crearán los buckets."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "La variable project_id no puede estar vacía."
  }
}

variable "default_location" {
  description = "Ubicación predeterminada utilizada cuando un bucket no define una ubicación específica."
  type        = string

  validation {
    condition     = length(trimspace(var.default_location)) > 0
    error_message = "La variable default_location no puede estar vacía."
  }
}

variable "default_labels" {
  description = "Etiquetas comunes aplicadas a todos los buckets administrados por el módulo."
  type        = map(string)
  default     = {}
}

variable "buckets" {
  description = "Definiciones de buckets Cloud Storage indexadas por una clave lógica estable."

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
    condition     = length(var.buckets) > 0
    error_message = "Debe definirse al menos un bucket."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.buckets) :
      can(regex(
        "^[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]$",
        bucket.name
      ))
    ])

    error_message = "Los nombres de bucket deben contener entre 3 y 63 caracteres y usar minúsculas, números, puntos, guiones o guiones bajos."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.buckets) :
      length(trimspace(bucket.purpose)) > 0
    ])

    error_message = "Cada bucket debe definir un purpose no vacío."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.buckets) :
      contains([
        "STANDARD",
        "NEARLINE",
        "COLDLINE",
        "ARCHIVE"
      ], bucket.storage_class)
    ])

    error_message = "storage_class debe ser STANDARD, NEARLINE, COLDLINE o ARCHIVE."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.buckets) :
      contains([
        "enforced",
        "inherited"
      ], bucket.public_access_prevention)
    ])

    error_message = "public_access_prevention debe ser enforced o inherited."
  }

  validation {
    condition = alltrue([
      for bucket in values(var.buckets) :
      bucket.soft_delete_retention_seconds == 0 ||
      (
        bucket.soft_delete_retention_seconds >= 604800 &&
        bucket.soft_delete_retention_seconds <= 7776000
      )
    ])

    error_message = "soft_delete_retention_seconds debe ser 0 o un valor entre 604800 y 7776000 segundos."
  }
}
