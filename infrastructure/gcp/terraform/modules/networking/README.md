# GCP Networking Module

Reusable Terraform module for the Event Management platform networking layer.

## Resources

This module creates:

- One custom-mode Google Cloud VPC.
- Multiple regional IPv4 subnets.
- Private Google Access on each subnet.
- An internal ingress firewall rule.
- A management-to-SSH ingress firewall rule.

## Design principles

- Do not use the Google Cloud default network.
- Do not create automatic regional subnets.
- Keep environment-specific CIDR ranges outside the reusable module.
- Do not expose SSH directly from the public Internet.
- Manage all platform networking resources through Terraform.
- Keep Cloud Router and Cloud NAT outside the initial implementation until
  private Compute Engine egress requirements are confirmed.

## Example

```hcl
module "networking" {
  source = "../../modules/networking"

  project_id   = var.project_id
  environment  = var.environment
  region       = var.region
  network_name = "event-management-dev-vpc"

  subnets = {
    management = {
      name                  = "event-management-dev-management"
      ip_cidr_range         = "10.10.0.0/24"
      region                = "us-central1"
      private_google_access = true
    }

    workloads = {
      name                  = "event-management-dev-workloads"
      ip_cidr_range         = "10.20.0.0/24"
      region                = "us-central1"
      private_google_access = true
    }

    data = {
      name                  = "event-management-dev-data"
      ip_cidr_range         = "10.30.0.0/24"
      region                = "us-central1"
      private_google_access = true
    }
  }
}
```

## Not included

The initial version does not create:

- Cloud Router.
- Cloud NAT.
- Private Service Access.
- Cloud DNS private zones.
- VPC peering.
- VPN or Interconnect.
- Load balancers.
- Public ingress rules.
