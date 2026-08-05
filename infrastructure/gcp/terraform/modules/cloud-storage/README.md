# Cloud Storage Module

Módulo Terraform reusable para crear y administrar buckets funcionales de Google Cloud Storage para la plataforma EventManagement.

## Alcance

El módulo administra uno o más buckets mediante un mapa de definiciones y `for_each`.

Cada bucket puede configurar:

- nombre globalmente único
- propósito
- ubicación
- clase de almacenamiento
- eliminación forzada
- Uniform Bucket-Level Access (UBLA)
- Public Access Prevention (PAP)
- versioning
- soft delete
- labels adicionales

## Recursos

- `google_storage_bucket`

## Fuera de alcance

La versión inicial no administra:

- el bucket utilizado por el backend remoto de Terraform
- IAM de buckets
- lifecycle rules
- retention policies
- Cloud KMS (CMEK)
- CORS
- website hosting
- access logging
- Autoclass
- objetos almacenados dentro de los buckets

El bucket del backend pertenece al ciclo de vida de Bootstrap y no debe importarse, recrearse ni modificarse desde este módulo.

## Dependencias

### Bootstrap

Bootstrap debe proporcionar:

- proyecto de Google Cloud
- API `storage.googleapis.com`
- bucket del backend remoto
- cuenta de servicio Terraform Deployer
- capacidad de impersonation

### IAM

La identidad Terraform Deployer debe disponer de permisos para administrar buckets. En DEV estos permisos se proporcionan mediante `roles/storage.admin`.

El módulo no crea ni modifica bindings IAM.

## Defaults de seguridad

- `force_destroy = false`
- `uniform_bucket_level_access = true`
- `public_access_prevention = "enforced"`
- `versioning_enabled = true`
- `soft_delete_retention_seconds = 604800`
- `storage_class = "STANDARD"`

## Inputs

### `project_id`

ID del proyecto de Google Cloud.

### `default_location`

Ubicación predeterminada.

### `default_labels`

Mapa de labels comunes.

### `buckets`

Mapa de buckets indexado por una clave lógica.

```hcl
buckets = {
  application = {
    name    = "example-project-event-management-dev-application"
    purpose = "application"
  }

  backups = {
    name               = "example-project-event-management-dev-backups"
    purpose            = "backups"
    storage_class      = "STANDARD"
    versioning_enabled = true
  }
}
```

## Ejemplo de uso

```hcl
module "cloud_storage" {
  source = "../../modules/cloud-storage"

  project_id       = var.project_id
  default_location = var.region

  default_labels = {
    application = var.platform_name
    environment = var.environment
    managed_by  = "terraform"
  }

  buckets = var.cloud_storage_buckets
}
```

## Outputs

- `bucket_ids`
- `bucket_names`
- `bucket_urls`
- `bucket_self_links`
- `bucket_locations`
- `bucket_storage_classes`
- `bucket_labels`

## Diseño

Los nombres físicos de los buckets se proporcionan explícitamente desde el ambiente consumidor.

El módulo no construye nombres internamente porque los nombres de Cloud Storage son globalmente únicos y deben permanecer desacoplados de la implementación reusable.

Los labels comunes se combinan con los labels específicos de cada bucket y el label `purpose` siempre se deriva del atributo `purpose`.

## Multiambiente

El módulo está diseñado para reutilizarse en:

- dev
- qa
- prod
- otros proyectos GCP

## Operación

La creación, modificación y eliminación de buckets debe realizarse únicamente mediante Terraform.

No debe utilizarse `gcloud storage buckets create` ni la consola de Google Cloud para crear recursos administrados por este módulo.
