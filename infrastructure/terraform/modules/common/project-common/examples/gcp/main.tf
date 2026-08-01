module "project_common" {
  source = "../.."

  organization        = var.organization
  platform            = var.platform
  platform_short      = var.platform_short
  cloud               = var.cloud
  environment         = var.environment
  customer            = var.customer
  region              = var.region
  owner               = var.owner
  repository          = var.repository
  cost_center         = var.cost_center
  data_classification = var.data_classification
  criticality         = var.criticality

  additional_labels = {
    deployment_phase = "foundation"
  }

  additional_tags = {
    DeploymentPhase = "foundation"
  }
}

variable "organization" {
  type = string
}

variable "platform" {
  type = string
}

variable "platform_short" {
  type = string
}

variable "cloud" {
  type = string
}

variable "environment" {
  type = string
}

variable "customer" {
  type = string
}

variable "region" {
  type = string
}

variable "owner" {
  type = string
}

variable "repository" {
  type = string
}

variable "cost_center" {
  type = string
}

variable "data_classification" {
  type = string
}

variable "criticality" {
  type = string
}

output "context" {
  value = module.project_common.context
}

output "name_prefix" {
  value = module.project_common.name_prefix
}

output "name_prefix_short" {
  value = module.project_common.name_prefix_short
}

output "name_prefix_compact" {
  value = module.project_common.name_prefix_compact
}

output "common_labels" {
  value = module.project_common.common_labels
}

output "common_tags" {
  value = module.project_common.common_tags
}

output "resource_names" {
  value = module.project_common.resource_names
}

output "module_metadata" {
  value = module.project_common.module_metadata
}
