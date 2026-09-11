# =============================================================================
# OS_08_13 — Continuous Delivery Foundation
#
# Establishes:
# - Dedicated Continuous Delivery execution identity.
# - Read-only Artifact Registry access.
# - Normalized release contract.
#
# This module does not create a runtime and does not deploy containers.
# =============================================================================

module "continuous_delivery" {
  source = "../../modules/continuous-delivery"

  project_id             = var.project_id
  region                 = module.artifact_registry.repository_location
  artifact_repository_id = module.artifact_registry.repository_id

  platform_name = var.platform_name
  environment   = var.environment

  service_account_id              = "continuous-delivery-executor"
  service_account_display_name    = "Continuous Delivery Executor"
  service_account_description     = "Validates and executes controlled release delivery operations for OPEN EVENT MANAGEMENT."
  service_account_deletion_policy = "PREVENT"

  services = {
    event-gateway = {
      container_name = "event-gateway"
      container_port = 8081

      health = {
        type = "functional"
        path = "/api/v1/gateway"
      }

      dependencies = ["kafka"]
      secret_refs  = []
    }

    integration-worker = {
      container_name = "event-integration-worker"
      container_port = 8083

      health = {
        type       = "smallrye"
        path       = "/health"
        live_path  = "/health/live"
        ready_path = "/health/ready"
      }

      dependencies = [
        "kafka",
        "servicenow",
      ]

      secret_refs = [
        "servicenow_credentials",
      ]
    }

    event-state-service = {
      container_name = "event-state-service"
      container_port = 8084

      health = {
        type       = "smallrye"
        path       = "/health"
        live_path  = "/health/live"
        ready_path = "/health/ready"
      }

      dependencies = [
        "kafka",
        "opensearch",
        "postgres",
      ]

      secret_refs = [
        "database_credentials",
      ]
    }
  }
}
