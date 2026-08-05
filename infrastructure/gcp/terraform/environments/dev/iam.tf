module "iam" {
  source = "../../modules/iam"

  project_id                       = var.project_id
  terraform_deployer_email         = var.terraform_deployer_email
  terraform_deployer_project_roles = var.terraform_deployer_project_roles

  service_agent_project_roles = {
    cloud_build = {
      member = "serviceAccount:service-${var.project_number}@gcp-sa-cloudbuild.iam.gserviceaccount.com"

      roles = [
        "roles/cloudbuild.serviceAgent",
      ]
    }
  }
}
