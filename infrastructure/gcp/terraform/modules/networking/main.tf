resource "google_compute_network" "this" {
  project                 = var.project_id
  name                    = var.network_name
  description             = "Custom VPC for Event Management ${var.environment}"
  auto_create_subnetworks = false
  routing_mode            = var.routing_mode
  mtu                     = 1460
}

resource "google_compute_subnetwork" "this" {
  for_each = var.subnets

  project                  = var.project_id
  name                     = each.value.name
  description              = try(each.value.description, null)
  region                   = each.value.region
  network                  = google_compute_network.this.id
  ip_cidr_range            = each.value.ip_cidr_range
  private_ip_google_access = try(each.value.private_google_access, true)
  stack_type               = try(each.value.stack_type, "IPV4_ONLY")
}

resource "google_compute_firewall" "allow_internal" {
  count = var.enable_internal_firewall ? 1 : 0

  project     = var.project_id
  name        = "${var.network_name}-allow-internal"
  description = "Allow internal communication between Event Management subnets"
  network     = google_compute_network.this.name
  direction   = "INGRESS"
  priority    = 1000

  source_ranges = local.subnet_cidr_ranges

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
  }

  allow {
    protocol = "udp"
  }
}

resource "google_compute_firewall" "allow_management_ssh" {
  count = var.enable_management_ssh ? 1 : 0

  project     = var.project_id
  name        = "${var.network_name}-allow-management-ssh"
  description = "Allow SSH from management subnet to instances tagged ssh-enabled"
  network     = google_compute_network.this.name
  direction   = "INGRESS"
  priority    = 900

  source_ranges = [local.management_subnet_cidr]
  target_tags   = ["ssh-enabled"]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}
