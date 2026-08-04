module "networking" {
  source = "../../modules/networking"

  project_id   = var.project_id
  environment  = var.environment
  region       = var.region
  network_name = var.network_name
  routing_mode = var.network_routing_mode

  subnets = var.network_subnets

  enable_internal_firewall = var.enable_internal_firewall
  enable_management_ssh    = var.enable_management_ssh
  management_subnet_key    = "management"
}
