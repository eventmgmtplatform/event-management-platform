resource "google_project_iam_member" "terraform_deployer_roles" {
  for_each = var.terraform_deployer_project_roles

  project = var.project_id
  role    = each.value
  member  = local.terraform_deployer_member
}
