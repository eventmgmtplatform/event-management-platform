# Changelog — GCP / ambiente dev

Configuración y convenciones registradas en este ámbito; fechas de commits, no fechas de despliegue.

[Índice y política](../../../../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Se incorpora reconstrucción histórica; no se aplican cambios Terraform en esta tarea.

## Historial confirmado en Git

### 2026-08-08 — `67c190ece54d`

- Cambio registrado: feat(os-08-13): add continuous delivery foundation.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/continuous-delivery.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`.

### 2026-08-06 — `1e13b6103547`

- Cambio registrado: feat(cloud-build): add reusable Artifact Registry image pipelines.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-build.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-04 — `ea9530133f6f`

- Cambio registrado: fix(terraform): revoke temporary Secret Manager admin from Cloud Build service agent.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`.

### 2026-08-04 — `367d6696bc5d`

- Cambio registrado: feat(terraform): adopt GitHub connection and link repository.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `b23b20375d17`

- Cambio registrado: fix(terraform): grant temporary Secret Manager admin to Cloud Build service agent.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`.

### 2026-08-04 — `7186231c67de`

- Cambio registrado: fix(terraform): adopt Cloud Build service agent role.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`.

### 2026-08-04 — `8e508809c165`

- Cambio registrado: feat(terraform): add GCP Cloud Build foundation.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-build.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-04 — `c6899b3a4cb0`

- Cambio registrado: fix(terraform): grant Cloud Build administration to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `c6e915d4f77a`

- Cambio registrado: feat(terraform): add GCP Secret Manager module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/secret-manager.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-04 — `35d6b40baa51`

- Cambio registrado: fix(terraform): grant Secret Manager admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `3dcd749baf12`

- Cambio registrado: feat(terraform): add GCP Cloud Storage module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-storage.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-04 — `841af71617f9`

- Cambio registrado: feat(terraform): grant Storage admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `133af4258aa6`

- Cambio registrado: feat(terraform): add GCP Artifact Registry module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/artifact-registry.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-04 — `25040e795edb`

- Cambio registrado: feat(terraform): grant Artifact Registry admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `3aeb90ccbd12`

- Cambio registrado: feat(terraform): add GCP networking module for dev.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/networking.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-08-01 — `5a5ca507f1c8`

- Cambio registrado: feat(terraform): add reusable IAM module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf`.

### 2026-07-31 — `4ed309ec2bed`

- Cambio registrado: chore: configure dev Terraform GCS backend.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/backend.tf`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/.terraform.lock.hcl`, `infrastructure/gcp/terraform/environments/dev/backend.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/providers.tf` y 3 archivo(s) adicional(es).
