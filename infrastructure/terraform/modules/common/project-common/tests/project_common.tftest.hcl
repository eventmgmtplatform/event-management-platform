run "gcp_shared_dev" {
  command = plan

  variables {
    organization        = "event-management"
    platform            = "event-management"
    platform_short      = "em"
    cloud               = "gcp"
    environment         = "dev"
    customer            = "shared"
    region              = "northamerica"
    owner               = "event-management-team"
    repository          = "event-management-platform"
    cost_center         = "transformation"
    data_classification = "internal"
    criticality         = "medium"

    additional_labels = {
      deployment_phase = "foundation"
    }
  }

  assert {
    condition     = output.name_prefix == "em-shared-dev-gcp"
    error_message = "El prefijo estándar GCP no coincide con el valor esperado."
  }

  assert {
    condition     = output.name_prefix_short == "em-shared-dev-gcp"
    error_message = "El prefijo corto GCP no coincide con el valor esperado."
  }

  assert {
    condition     = output.name_prefix_compact == "emshareddevgcp"
    error_message = "El prefijo compacto GCP no coincide con el valor esperado."
  }

  assert {
    condition     = output.common_labels["managed_by"] == "terraform"
    error_message = "El label managed_by debe conservar el valor corporativo terraform."
  }

  assert {
    condition     = output.common_labels["deployment_phase"] == "foundation"
    error_message = "No se integró el label adicional."
  }
}

run "aws_customer_prod" {
  command = plan

  variables {
    organization        = "event-management"
    platform            = "event-management"
    platform_short      = "em"
    cloud               = "aws"
    environment         = "prod"
    customer            = "customer01"
    region              = "northamerica"
    owner               = "event-management-team"
    repository          = "event-management-platform"
    cost_center         = "transformation"
    data_classification = "confidential"
    criticality         = "high"
  }

  assert {
    condition     = output.name_prefix == "em-customer01-prod-aws"
    error_message = "El prefijo AWS no coincide con el valor esperado."
  }

  assert {
    condition     = output.common_tags["Cloud"] == "aws"
    error_message = "El tag Cloud debería ser aws."
  }
}

run "azure_customer_test" {
  command = plan

  variables {
    organization        = "event-management"
    platform            = "event-management"
    platform_short      = "em"
    cloud               = "azure"
    environment         = "test"
    customer            = "customer02"
    region              = "northamerica"
    owner               = "event-management-team"
    repository          = "event-management-platform"
    cost_center         = "transformation"
    data_classification = "internal"
    criticality         = "medium"
  }

  assert {
    condition     = output.name_prefix == "em-customer02-test-azure"
    error_message = "El prefijo Azure no coincide con el valor esperado."
  }
}

run "mandatory_labels_cannot_be_overridden" {
  command = plan

  variables {
    organization        = "event-management"
    platform            = "event-management"
    platform_short      = "em"
    cloud               = "gcp"
    environment         = "dev"
    customer            = "shared"
    region              = "northamerica"
    owner               = "event-management-team"
    repository          = "event-management-platform"
    cost_center         = "transformation"
    data_classification = "internal"
    criticality         = "medium"

    additional_labels = {
      managed_by = "manual"
    }
  }

  assert {
    condition     = output.common_labels["managed_by"] == "terraform"
    error_message = "Los labels adicionales no deben sobrescribir los labels corporativos."
  }
}
