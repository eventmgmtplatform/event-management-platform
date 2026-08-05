module "secret_manager" {
  source = "../../modules/secret-manager"

  project_id = var.project_id

  default_labels = {
    application = var.platform_name
    environment = var.environment
    managed_by  = "terraform"
  }

  secrets = var.secret_manager_secrets
}
