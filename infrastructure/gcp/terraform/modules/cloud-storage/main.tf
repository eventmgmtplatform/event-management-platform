resource "google_storage_bucket" "this" {

  for_each = local.buckets

  project = var.project_id

  name          = each.value.name
  location      = each.value.location
  storage_class = each.value.storage_class

  labels = each.value.labels

  force_destroy = each.value.force_destroy

  uniform_bucket_level_access = each.value.uniform_bucket_level_access

  public_access_prevention = each.value.public_access_prevention

  versioning {
    enabled = each.value.versioning_enabled
  }

  soft_delete_policy {
    retention_duration_seconds = each.value.soft_delete_retention_seconds
  }
}
