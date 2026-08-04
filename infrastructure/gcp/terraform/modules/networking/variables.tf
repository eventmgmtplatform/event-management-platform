variable "project_id" {
  description = "Google Cloud project ID where networking resources will be created."
  type        = string
}

variable "environment" {
  description = "Deployment environment, for example dev, qa, or prod."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "The environment must be one of: dev, qa, prod."
  }
}

variable "region" {
  description = "Primary Google Cloud region for regional networking resources."
  type        = string
}

variable "network_name" {
  description = "Name of the custom-mode VPC network."
  type        = string
}

variable "routing_mode" {
  description = "Network-wide dynamic routing mode."
  type        = string
  default     = "REGIONAL"

  validation {
    condition     = contains(["REGIONAL", "GLOBAL"], var.routing_mode)
    error_message = "The routing mode must be REGIONAL or GLOBAL."
  }
}

variable "subnets" {
  description = "Subnet definitions keyed by logical subnet name."

  type = map(object({
    name                  = string
    ip_cidr_range         = string
    region                = string
    private_google_access = optional(bool, true)
    description           = optional(string)
    stack_type            = optional(string, "IPV4_ONLY")
  }))

  validation {
    condition     = length(var.subnets) > 0
    error_message = "At least one subnet must be defined."
  }
}

variable "enable_internal_firewall" {
  description = "Create a rule allowing internal communication between platform subnets."
  type        = bool
  default     = true
}

variable "enable_management_ssh" {
  description = "Create an SSH rule from the management subnet to instances tagged ssh-enabled."
  type        = bool
  default     = true
}

variable "management_subnet_key" {
  description = "Map key identifying the management subnet."
  type        = string
  default     = "management"
}
