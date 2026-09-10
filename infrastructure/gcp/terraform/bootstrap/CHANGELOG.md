# Changelog — GCP / bootstrap

Configuración y convenciones registradas en este ámbito; fechas de commits, no fechas de despliegue.

[Índice y política](../../../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Se incorpora reconstrucción histórica; no se aplican cambios Terraform en esta tarea.

## Historial confirmado en Git

### 2026-08-04 — `3db90c3f0a99`

- Cambio registrado: fix(terraform): manage Cloud Build APIs in bootstrap.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-04 — `de81511421a8`

- Cambio registrado: fix(terraform): manage Secret Manager API in bootstrap.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-04 — `25040e795edb`

- Cambio registrado: feat(terraform): grant Artifact Registry admin to deployer.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-01 — `49f61570e9e7`

- Cambio registrado: feat(gcp-bootstrap): enable Compute Engine API.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`.

### 2026-08-01 — `c10a9a7e8c93`

- Cambio registrado: feat(terraform): enable service account impersonation.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/main.tf`, `infrastructure/gcp/terraform/bootstrap/outputs.tf`, `infrastructure/gcp/terraform/bootstrap/terraform.tfvars.example`, `infrastructure/gcp/terraform/bootstrap/variables.tf`.

### 2026-07-31 — `7272150ed845`

- Cambio registrado: feat: bootstrap Terraform remote state on GCS.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/.gitkeep`, `infrastructure/gcp/terraform/bootstrap/.terraform.lock.hcl`, `infrastructure/gcp/terraform/bootstrap/backend.tf`, `infrastructure/gcp/terraform/bootstrap/main.tf` y 5 archivo(s) adicional(es).

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/gcp/terraform/bootstrap/.gitkeep`.
