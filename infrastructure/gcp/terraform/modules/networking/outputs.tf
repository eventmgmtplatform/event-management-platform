output "network_id" {
  description = "Fully qualified identifier of the VPC network."
  value       = google_compute_network.this.id
}

output "network_name" {
  description = "Name of the VPC network."
  value       = google_compute_network.this.name
}

output "network_self_link" {
  description = "Self-link of the VPC network."
  value       = google_compute_network.this.self_link
}

output "subnet_ids" {
  description = "Map of subnet IDs keyed by logical subnet name."

  value = {
    for key, subnet in google_compute_subnetwork.this :
    key => subnet.id
  }
}

output "subnet_names" {
  description = "Map of subnet names keyed by logical subnet name."

  value = {
    for key, subnet in google_compute_subnetwork.this :
    key => subnet.name
  }
}

output "subnet_self_links" {
  description = "Map of subnet self-links keyed by logical subnet name."

  value = {
    for key, subnet in google_compute_subnetwork.this :
    key => subnet.self_link
  }
}

output "subnet_cidr_ranges" {
  description = "Map of subnet CIDR ranges keyed by logical subnet name."

  value = {
    for key, subnet in google_compute_subnetwork.this :
    key => subnet.ip_cidr_range
  }
}
