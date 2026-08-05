resource "google_project_iam_member" "terraform_deployer_roles" {
  for_each = var.terraform_deployer_project_roles

  project = var.project_id
  role    = each.value
  member  = local.terraform_deployer_member
}


# OS_08_08_4_SERVICE_AGENT_BINDINGS
resource "google_project_iam_member" "service_agent_roles" {
  for_each = local.service_agent_role_bindings

  project = var.project_id
  role    = each.value.role
  member  = each.value.member
}
# END_OS_08_08_4_SERVICE_AGENT_BINDINGS
