variable "project_id" {
  description = "ID del proyecto de Google Cloud donde se administrarán los bindings IAM."
  type        = string

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "La variable project_id no puede estar vacía."
  }
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
  description = "Roles de proyecto asignados a la cuenta terraform-deployer."
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


# OS_08_08_4_SERVICE_AGENT_ROLES
variable "service_agent_project_roles" {
  description = "Project-level IAM roles assigned to Google-managed service agents."

  type = map(object({
    member = string
    roles  = set(string)
  }))

  default = {}

  validation {
    condition = alltrue([
      for agent in values(var.service_agent_project_roles) :
      startswith(agent.member, "serviceAccount:service-")
    ])
    error_message = "Every service-agent member must begin with serviceAccount:service-."
  }

  validation {
    condition = alltrue(flatten([
      for agent in values(var.service_agent_project_roles) : [
        for role in agent.roles :
        startswith(role, "roles/")
      ]
    ]))
    error_message = "Every service-agent role must use the roles/name format."
  }
}
# END_OS_08_08_4_SERVICE_AGENT_ROLES
