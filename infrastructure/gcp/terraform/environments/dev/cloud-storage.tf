module "cloud_storage" {
  source = "../../modules/cloud-storage"

  project_id       = var.project_id
  default_location = var.region

  default_labels = {
    application = var.platform_name
    environment = var.environment
    managed_by  = "terraform"
    module      = "cloud-storage"
  }

  buckets = var.cloud_storage_buckets
}
