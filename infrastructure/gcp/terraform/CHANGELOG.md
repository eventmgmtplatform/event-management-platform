# Changelog — Terraform GCP

Bootstrap GCS e impersonación; IAM, APIs y módulos de red, registro de imágenes, almacenamiento, secretos, builds y entrega continua.

[Índice y política](../../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Sin cambio funcional nuevo documentado en este corte; se agrega trazabilidad histórica.

## Historial confirmado en Git

### 2026-08-08 — `67c190ece54d`

- Cambio registrado: feat(os-08-13): add continuous delivery foundation.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/continuous-delivery.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/modules/continuous-delivery/README.md`, `infrastructure/gcp/terraform/modules/continuous-delivery/locals.tf` y 4 archivo(s) adicional(es).

### 2026-08-06 — `1e13b6103547`

- Cambio registrado: feat(cloud-build): add reusable Artifact Registry image pipelines.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-build.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 3 archivo(s) adicional(es).

### 2026-08-04 — `ea9530133f6f`

- Cambio registrado: fix(terraform): revoke temporary Secret Manager admin from Cloud Build service agent.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`.

### 2026-08-04 — `367d6696bc5d`

- Cambio registrado: feat(terraform): adopt GitHub connection and link repository.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/modules/cloud-build/README.md`.

### 2026-08-04 — `b23b20375d17`

- Cambio registrado: fix(terraform): grant temporary Secret Manager admin to Cloud Build service agent.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`.

### 2026-08-04 — `7186231c67de`

- Cambio registrado: fix(terraform): adopt Cloud Build service agent role.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/modules/iam/locals.tf`, `infrastructure/gcp/terraform/modules/iam/main.tf` y 2 archivo(s) adicional(es).

### 2026-08-04 — `8e508809c165`

- Cambio registrado: feat(terraform): add GCP Cloud Build foundation.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-build.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-04 — `c6899b3a4cb0`

- Cambio registrado: fix(terraform): grant Cloud Build administration to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `3db90c3f0a99`

- Cambio registrado: fix(terraform): manage Cloud Build APIs in bootstrap.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-04 — `c6e915d4f77a`

- Cambio registrado: feat(terraform): add GCP Secret Manager module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/secret-manager.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-04 — `35d6b40baa51`

- Cambio registrado: fix(terraform): grant Secret Manager admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `de81511421a8`

- Cambio registrado: fix(terraform): manage Secret Manager API in bootstrap.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-04 — `3dcd749baf12`

- Cambio registrado: feat(terraform): add GCP Cloud Storage module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/cloud-storage.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-04 — `841af71617f9`

- Cambio registrado: feat(terraform): grant Storage admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/modules/iam/README.md`.

### 2026-08-04 — `94242ef73f5a`

- Cambio registrado: chore(terraform): add OS_08_10 operational scripts.
- Alcance en este componente: `infrastructure/gcp/terraform/scripts/os-08-10/00-common.sh`, `infrastructure/gcp/terraform/scripts/os-08-10/01-prerequisites-and-inventory.sh`, `infrastructure/gcp/terraform/scripts/os-08-10/02-remediation-gate.sh`, `infrastructure/gcp/terraform/scripts/os-08-10/03-terraform-execution.sh` y 2 archivo(s) adicional(es).

### 2026-08-04 — `133af4258aa6`

- Cambio registrado: feat(terraform): add GCP Artifact Registry module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/artifact-registry.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-04 — `25040e795edb`

- Cambio registrado: feat(terraform): grant Artifact Registry admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`.

### 2026-08-04 — `3aeb90ccbd12`

- Cambio registrado: feat(terraform): add GCP networking module for dev.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/networking.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-01 — `5a5ca507f1c8`

- Cambio registrado: feat(terraform): add reusable IAM module.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/iam.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example`, `infrastructure/gcp/terraform/environments/dev/variables.tf` y 6 archivo(s) adicional(es).

### 2026-08-01 — `49f61570e9e7`

- Cambio registrado: feat(gcp-bootstrap): enable Compute Engine API.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-01 — `c10a9a7e8c93`

- Cambio registrado: feat(terraform): enable service account impersonation.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`, `infrastructure/gcp/terraform/bootstrap/outputs.tf`, `infrastructure/gcp/terraform/bootstrap/terraform.tfvars.example`, `infrastructure/gcp/terraform/bootstrap/variables.tf`.

### 2026-07-31 — `4ed309ec2bed`

- Cambio registrado: chore: configure dev Terraform GCS backend.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/backend.tf`.

### 2026-07-31 — `7272150ed845`

- Cambio registrado: feat: bootstrap Terraform remote state on GCS.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/.gitkeep`, `infrastructure/gcp/terraform/bootstrap/.terraform.lock.hcl`, `infrastructure/gcp/terraform/bootstrap/backend.tf`, `infrastructure/gcp/terraform/bootstrap/main.tf` y 5 archivo(s) adicional(es).

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/.gitkeep`, `infrastructure/gcp/terraform/environments/dev/.terraform.lock.hcl`, `infrastructure/gcp/terraform/environments/dev/backend.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf` y 8 archivo(s) adicional(es).
