locals {
  module_name    = "project-common"
  module_version = "0.1.0"

  normalized = {
    organization        = trimspace(var.organization)
    platform            = trimspace(var.platform)
    platform_short      = trimspace(var.platform_short)
    cloud               = trimspace(var.cloud)
    environment         = trimspace(var.environment)
    customer            = trimspace(var.customer)
    region              = trimspace(var.region)
    owner               = trimspace(var.owner)
    managed_by          = trimspace(var.managed_by)
    repository          = trimspace(var.repository)
    cost_center         = trimspace(var.cost_center)
    data_classification = trimspace(var.data_classification)
    criticality         = trimspace(var.criticality)
  }

  name_parts = [
    local.normalized.platform_short,
    local.normalized.customer,
    local.normalized.environment,
    local.normalized.cloud
  ]

  name_prefix = join("-", local.name_parts)

  name_prefix_short = join("-", [
    local.normalized.platform_short,
    substr(local.normalized.customer, 0, 8),
    local.normalized.environment,
    local.normalized.cloud
  ])

  name_prefix_compact = join("", local.name_parts)

  base_labels = {
    platform            = local.normalized.platform
    environment         = local.normalized.environment
    customer            = local.normalized.customer
    cloud               = local.normalized.cloud
    region              = local.normalized.region
    owner               = local.normalized.owner
    managed_by          = local.normalized.managed_by
    repository          = local.normalized.repository
    cost_center         = local.normalized.cost_center
    data_classification = local.normalized.data_classification
    criticality         = local.normalized.criticality
  }

  base_tags = {
    Platform           = local.normalized.platform
    Environment        = local.normalized.environment
    Customer           = local.normalized.customer
    Cloud              = local.normalized.cloud
    Region             = local.normalized.region
    Owner              = local.normalized.owner
    ManagedBy          = local.normalized.managed_by
    Repository         = local.normalized.repository
    CostCenter         = local.normalized.cost_center
    DataClassification = local.normalized.data_classification
    Criticality        = local.normalized.criticality
  }

  # Los valores corporativos obligatorios tienen prioridad.
  common_labels = merge(
    var.additional_labels,
    local.base_labels
  )

  common_tags = merge(
    var.additional_tags,
    local.base_tags
  )

  context = merge(local.normalized, {
    name_prefix         = local.name_prefix
    name_prefix_short   = local.name_prefix_short
    name_prefix_compact = local.name_prefix_compact
  })

  resource_names = {
    network                 = "${local.name_prefix}-network"
    subnet_data             = "${local.name_prefix}-data"
    subnet_administration   = "${local.name_prefix}-admin"
    subnet_workloads        = "${local.name_prefix}-workloads"
    service_account         = "${local.name_prefix_short}-service"
    artifact_repository     = "${local.name_prefix}-artifacts"
    secrets                 = "${local.name_prefix}-secrets"
    logs                    = "${local.name_prefix}-logs"
    monitoring              = "${local.name_prefix}-monitoring"
    private_service_connect = "${local.name_prefix}-private"
  }
}
