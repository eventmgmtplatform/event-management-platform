locals {
  subnet_cidr_ranges = [
    for subnet in values(var.subnets) : subnet.ip_cidr_range
  ]

  management_subnet_cidr = var.subnets[var.management_subnet_key].ip_cidr_range
}
