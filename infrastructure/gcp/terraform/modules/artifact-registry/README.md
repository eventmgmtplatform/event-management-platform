# Artifact Registry Module

Módulo reusable para crear un repositorio Google Artifact Registry.

## Alcance inicial

OS_08_09 crea un repositorio:

- regional;
- formato Docker;
- modo estándar;
- administrado completamente mediante Terraform.

## Recursos

- `google_artifact_registry_repository`

## Seguridad

El módulo no crea bindings IAM.

Los permisos de lectura y escritura deben asignarse posteriormente a identidades
específicas de CI/CD o runtime.

El Terraform Deployer administra el recurso mediante:

- `roles/artifactregistry.admin`

## Ejemplo

```hcl
module "artifact_registry" {
  source = "../../modules/artifact-registry"

  project_id    = var.project_id
  location      = var.artifact_registry_location
  repository_id = var.artifact_registry_repository_id
  description   = var.artifact_registry_description

  format         = "DOCKER"
  mode           = "STANDARD_REPOSITORY"
  immutable_tags = false

  labels = {
    application = "event-management"
    environment = "dev"
    managed_by  = "terraform"
    purpose     = "container-images"
  }
}
