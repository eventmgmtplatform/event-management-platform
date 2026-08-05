locals {
  terraform_deployer_member = "serviceAccount:${var.terraform_deployer_email}"
}


# OS_08_08_4_SERVICE_AGENT_LOCALS
locals {
  service_agent_role_bindings = merge([
    for agent_key, agent in var.service_agent_project_roles : {
      for role in agent.roles :
      "${agent_key}|${role}" => {
        member = agent.member
        role   = role
      }
    }
  ]...)
}
# END_OS_08_08_4_SERVICE_AGENT_LOCALS
