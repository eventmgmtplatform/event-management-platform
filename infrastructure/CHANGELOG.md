# Changelog — Infraestructura local y cloud

Compose y servicios de soporte, migraciones PostgreSQL, topics Kafka y despliegue cloud. Los módulos cloud tienen además su changelog propio.

[Índice y política](../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Kafka/Kafbat: receta independiente reproducible con digests, red/volumen propios,
  healthcheck interno y configuración estática de UI; no migra el runtime activo.

- OS_11_01.IMP: migraciones aditivas 020 (coordinación Processor) y 021 (entrega/recovery Worker), sin borrar datos ni offsets. Laboratorio E2E aislado definido bajo testing/environments; no modifica servicios compartidos.

- Compose referencia mocks en testing/mocks; ambiente CACF trasladado a testing/environments. El workspace incluye nuevas migraciones ESS 016/017 y actualización de topics; aún sin commit.

## Historial confirmado en Git

### 2026-09-09 — `887baeaf51f1`

- Cambio registrado: feat(processor): add independent aiops CRUD and mock provider.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/mock-integrations/aiops/mappings/assess.json`, `infrastructure/postgres/init/015-processor-aiops.sql`.

### 2026-09-09 — `068fae1be8ca`

- Cambio registrado: feat(processor): persist correlation and group command decisions atomically.
- Alcance en este componente: `infrastructure/postgres/init/013-processor-correlation.sql`, `infrastructure/postgres/init/014-processor-command-ledger.sql`.

### 2026-09-09 — `d06fd735ff02`

- Cambio registrado: feat(processor): add functional REST administration and versioned blackouts.
- Alcance en este componente: `infrastructure/postgres/init/012-processor-administration.sql`.

### 2026-09-09 — `b059aaf8cdbe`

- Cambio registrado: feat(event-processor): add versioned typed policy configuration.
- Alcance en este componente: `infrastructure/postgres/init/011-processor-rule-registry.sql`.

### 2026-09-09 — `54aaa8de1d1e`

- Cambio registrado: feat(event-processor): persist output retries and verify recovery.
- Alcance en este componente: `infrastructure/postgres/init/010-processor-outbox-recovery.sql`.

### 2026-09-09 — `baf778df7160`

- Cambio registrado: feat(event-processor): replace enrichment with durable processing foundation.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/postgres/init/009-event-processor.sql`.

### 2026-09-09 — `3e78b3a6ec5e`

- Cambio registrado: chore(git): consolidate pending workstreams for main.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-09-08 — `1c3ede3e9d9a`

- Cambio registrado: feat: add ecosystem service administration and master tests.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `infrastructure/docker-compose.cacf-test.yml`, `infrastructure/docker-compose.cacf.yml`, `infrastructure/mock-integrations/cacf-servicenow/mappings/lookup.json`, `infrastructure/mock-integrations/cacf-servicenow/mappings/update.json` y 3 archivo(s) adicional(es).

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `infrastructure/docker-compose.event-management-console.yml`, `infrastructure/docker-compose.itsm-dashboard.yml`, `infrastructure/docker-compose.yml`, `infrastructure/kafka/create-topics.sh`.

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `infrastructure/mock-integrations/gnm/__files/close-success.json`, `infrastructure/mock-integrations/gnm/__files/duplicate-close.json`, `infrastructure/mock-integrations/gnm/__files/incident-closed.json`, `infrastructure/mock-integrations/gnm/__files/incident-open.json` y 9 archivo(s) adicional(es).

### 2026-09-05 — `85af2ff1d7d3`

- Cambio registrado: feat(console): add event management console foundation.
- Alcance en este componente: `infrastructure/docker-compose.event-management-console.yml`.

### 2026-09-05 — `b2475908289a`

- Cambio registrado: feat(servicenow): add pull restart recovery coordinator.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-09-04 — `a20c7c0a71c1`

- Cambio registrado: feat(servicenow): add persistent operational control.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/postgres/init/006-integration-worker-control.sql`.

### 2026-09-04 — `74d75b0f4dec`

- Cambio registrado: feat(servicenow): add safe stale command reconciliation.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/mock-integrations/servicenow/mappings/lookup-incident-not-found.json`, `infrastructure/postgres/init/005-integration-command-recovery-lease.sql`.

### 2026-09-03 — `c3a3da450949`

- Cambio registrado: feat(servicenow): add durable command idempotency.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/postgres/init/004-integration-command-idempotency.sql`.

### 2026-09-02 — `2c1d77275c7a`

- Cambio registrado: feat(servicenow): add controlled retry policy.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-09-02 — `3bc9cdf2e1b9`

- Cambio registrado: feat(servicenow): add Kafka-aware worker readiness.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-08-13 — `741f631080e1`

- Cambio registrado: feat(os-01-01): add events enrichment pass-through.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-08-13 — `cff1e46e3a28`

- Cambio registrado: feat(os-01-01): add enrichment engine foundation.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-08-12 — `f8b4a43fdd51`

- Cambio registrado: feat(os-01-01): add safe local runtime controls.
- Alcance en este componente: `infrastructure/docker-compose.yml`.

### 2026-08-12 — `2b138dbcbd59`

- Cambio registrado: fix(os-08-14): harden runtime persistence and result idempotency.
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/postgres/init/003-integration-result-idempotency.sql`.

### 2026-08-08 — `67c190ece54d`

- Cambio registrado: feat(os-08-13): add continuous delivery foundation.
- Alcance en este componente: `infrastructure/gcp/terraform/environments/dev/continuous-delivery.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/modules/continuous-delivery/README.md`, `infrastructure/gcp/terraform/modules/continuous-delivery/locals.tf` y 4 archivo(s) adicional(es).

### 2026-08-06 — `1e13b6103547`

- Cambio registrado: feat(cloud-build): add reusable Artifact Registry image pipelines.
- Alcance en este componente: `cloudbuild.yaml`, `infrastructure/gcp/terraform/environments/dev/cloud-build.tf`, `infrastructure/gcp/terraform/environments/dev/outputs.tf`, `infrastructure/gcp/terraform/environments/dev/terraform.tfvars.example` y 4 archivo(s) adicional(es).

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

### 2026-08-01 — `7c0e54dce5fc`

- Cambio registrado: feat(terraform): add project-common conventions module.
- Alcance en este componente: `infrastructure/terraform/modules/aws/.gitkeep`, `infrastructure/terraform/modules/azure/.gitkeep`, `infrastructure/terraform/modules/common/project-common/CHANGELOG.md`, `infrastructure/terraform/modules/common/project-common/README.md` y 12 archivo(s) adicional(es).

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
- Alcance en este componente: `infrastructure/docker-compose.yml`, `infrastructure/gcp/cloudbuild/.gitkeep`, `infrastructure/gcp/terraform/bootstrap/.gitkeep`, `infrastructure/gcp/terraform/environments/dev/.terraform.lock.hcl` y 14 archivo(s) adicional(es).
