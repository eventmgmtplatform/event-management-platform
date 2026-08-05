resource "google_secret_manager_secret" "this" {
  for_each = local.normalized_secrets

  project             = var.project_id
  secret_id           = each.value.secret_id
  labels              = each.value.labels
  deletion_protection = each.value.deletion_protection

  replication {
    dynamic "auto" {
      for_each = each.value.replication_type == "AUTOMATIC" ? [1] : []
      content {}
    }

    dynamic "user_managed" {
      for_each = each.value.replication_type == "USER_MANAGED" ? [1] : []

      content {
        dynamic "replicas" {
          for_each = each.value.replication_locations

          content {
            location = replicas.value
          }
        }
      }
    }
  }
}

resource "google_secret_manager_secret_iam_member" "this" {
  for_each = local.iam_members

  project   = var.project_id
  secret_id = google_secret_manager_secret.this[each.value.secret_key].secret_id
  role      = each.value.role
  member    = each.value.member
}
