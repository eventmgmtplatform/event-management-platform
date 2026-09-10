# Changelog — Terraform / project-common

Configuración y convenciones registradas en este ámbito; fechas de commits, no fechas de despliegue.

[Índice y política](../../../../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Pruebas Terraform centralizadas; contrato de salidas sin cambios por esta reorganización.

## Registro de versión preexistente


Todos los cambios relevantes de este módulo serán documentados aquí.

El formato está basado en Keep a Changelog y el módulo utiliza Semantic
Versioning.

### [0.1.0] - 2026-08-01

#### Added

- Estructura inicial del módulo `project-common`.
- Contexto compartido para GCP, AWS y Azure.
- Convenciones de nombres.
- Labels y tags comunes.
- Variables globales de gobierno.
- Validaciones de proveedor, ambiente, cliente y clasificación.
- Outputs compartidos.
- Ejemplo de consumo para GCP.
- Pruebas automatizadas con `terraform test`.


## Historial confirmado en Git

### 2026-08-01 — `7c0e54dce5fc`

- Cambio registrado: feat(terraform): add project-common conventions module.
- Alcance en este componente: `infrastructure/terraform/modules/common/project-common/CHANGELOG.md`, `infrastructure/terraform/modules/common/project-common/README.md`, `infrastructure/terraform/modules/common/project-common/examples/gcp/main.tf`, `infrastructure/terraform/modules/common/project-common/examples/gcp/terraform.tfvars.example` y 6 archivo(s) adicional(es).
