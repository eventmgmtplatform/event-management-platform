output "terraform_deployer_member" {
  description = "Identificador IAM de la cuenta de servicio terraform-deployer."
  value       = local.terraform_deployer_member
}

output "terraform_deployer_project_roles" {
  description = "Roles de proyecto administrados para terraform-deployer."
  value       = sort(tolist(var.terraform_deployer_project_roles))
}

output "terraform_deployer_role_bindings" {
  description = "Bindings IAM creados para terraform-deployer."
  value = {
    for role, binding in google_project_iam_member.terraform_deployer_roles :
    role => binding.id
  }
}


# OS_08_08_4_SERVICE_AGENT_OUTPUTS
output "service_agent_role_bindings" {
  description = "Project IAM bindings assigned to Google-managed service agents."

  value = {
    for key, binding in google_project_iam_member.service_agent_roles :
    key => binding.id
  }
}
# END_OS_08_08_4_SERVICE_AGENT_OUTPUTS
