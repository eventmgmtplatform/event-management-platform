resource "google_service_account" "execution" {
  project      = var.project_id
  account_id   = var.execution_service_account.account_id
  display_name = var.execution_service_account.display_name
  description  = var.execution_service_account.description

  deletion_policy = upper(var.execution_service_account.deletion_policy)
}

resource "google_project_iam_member" "execution_project_roles" {
  for_each = var.execution_project_roles

  project = var.project_id
  role    = each.value
  member  = local.execution_member
}

resource "google_artifact_registry_repository_iam_member" "execution_writer" {
  project    = var.project_id
  location   = var.artifact_registry.location
  repository = var.artifact_registry.repository_id
  role       = var.artifact_registry.writer_role
  member     = local.execution_member
}

resource "google_storage_bucket_iam_member" "source_reader" {
  count = length(trimspace(var.source_bucket.name)) > 0 ? 1 : 0

  bucket = var.source_bucket.name
  role   = var.source_bucket.reader_role
  member = local.execution_member
}

resource "google_secret_manager_secret_iam_member" "execution_accessor" {
  for_each = local.secret_access_bindings

  project   = var.project_id
  secret_id = each.value.secret_id
  role      = each.value.role
  member    = local.execution_member
}

resource "google_cloudbuildv2_connection" "github" {
  count = local.repository_connection_enabled ? 1 : 0

  project  = var.project_id
  location = var.location
  name     = var.repository_connection.name

  annotations = local.common_annotations

  github_config {
    app_installation_id = var.repository_connection.app_installation_id

    authorizer_credential {
      oauth_token_secret_version = var.repository_connection.oauth_secret_version
    }
  }

  deletion_policy = "PREVENT"
}

resource "google_cloudbuildv2_repository" "this" {
  for_each = local.repository_connection_enabled ? var.repositories : {}

  project           = var.project_id
  location          = var.location
  name              = each.value.name
  parent_connection = google_cloudbuildv2_connection.github[0].name
  remote_uri        = each.value.remote_uri

  annotations = local.common_annotations

  deletion_policy = "PREVENT"
}

resource "google_cloudbuild_trigger" "this" {
  for_each = local.repository_connection_enabled ? var.triggers : {}

  project  = var.project_id
  location = var.location

  name        = each.value.name
  description = "Managed Cloud Build trigger for ${each.key}."
  disabled    = each.value.disabled

  filename        = each.value.filename
  included_files  = each.value.included_files
  ignored_files   = each.value.ignored_files
  substitutions   = each.value.substitutions
  service_account = google_service_account.execution.name

  tags = [
    var.platform_name,
    var.environment,
    "terraform",
    "cloud-build"
  ]

  approval_config {
    approval_required = each.value.approval_required
  }

  repository_event_config {
    repository = google_cloudbuildv2_repository.this[each.value.repository_key].id

    dynamic "push" {
      for_each = contains(
        ["PUSH_BRANCH", "PUSH_TAG"],
        upper(each.value.event_type)
      ) ? [1] : []

      content {
        branch = upper(each.value.event_type) == "PUSH_BRANCH" ? each.value.branch_regex : null
        tag    = upper(each.value.event_type) == "PUSH_TAG" ? each.value.tag_regex : null
      }
    }

    dynamic "pull_request" {
      for_each = upper(each.value.event_type) == "PULL_REQUEST" ? [1] : []

      content {
        branch          = each.value.branch_regex
        comment_control = "COMMENTS_ENABLED_FOR_EXTERNAL_CONTRIBUTORS_ONLY"
      }
    }
  }

  depends_on = [
    google_project_iam_member.execution_project_roles,
    google_artifact_registry_repository_iam_member.execution_writer
  ]
}
