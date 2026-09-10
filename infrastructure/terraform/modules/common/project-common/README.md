# project-common

Módulo Terraform independiente del proveedor que define el contexto común de
Event Management Platform.

## Responsabilidades

- Definir convenciones de nombres.
- Identificar proveedor, ambiente y cliente.
- Generar labels compatibles con GCP.
- Generar tags comunes para AWS y Azure.
- Centralizar variables globales de gobierno.
- Proponer nombres para módulos consumidores.
- Validar entradas independientemente del proveedor.

## Fuera de alcance

Este módulo no crea recursos cloud. En particular, no crea:

- Redes o subredes.
- Identidades o roles IAM.
- Buckets.
- Máquinas virtuales.
- Clústeres.
- Bases de datos.
- Repositorios de artefactos.
- Secretos.

## Proveedores admitidos

- `gcp`
- `aws`
- `azure`

## Ambientes admitidos

- `dev`
- `test`
- `qa`
- `stage`
- `prod`
- `sandbox`

## Ejemplo

```hcl
module "project_common" {
  source = "../../../modules/common/project-common"

  organization   = "event-management"
  platform       = "event-management"
  platform_short = "em"
  cloud          = "gcp"
  environment    = "dev"
  customer       = "shared"
  region         = "northamerica"
  owner          = "event-management-team"
}
Salidas principales

Para el ejemplo anterior:

name_prefix         = em-shared-dev-gcp
name_prefix_short   = em-shared-dev-gcp
name_prefix_compact = emshareddevgcp

El módulo también publica:

context
common_labels
common_tags
resource_names
module_metadata
Prioridad de labels y tags

Los labels y tags corporativos obligatorios tienen prioridad sobre los valores
recibidos mediante additional_labels y additional_tags.

Esto evita que un módulo consumidor sobrescriba accidentalmente valores como:

Ambiente.
Cliente.
Proveedor.
Responsable.
Criticidad.
Herramienta de administración.
Validación

Desde el directorio del módulo:

terraform init -backend=false
terraform validate
terraform test
Versionamiento

El módulo utiliza Semantic Versioning:

MAJOR.MINOR.PATCH

La versión inicial de desarrollo es:

0.1.0

[Historial de cambios del componente](CHANGELOG.md).
