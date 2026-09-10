# Changelog — Despliegue / CI-CD

Layout de imágenes y entrega por manifiestos validados, pipelines reutilizables de Artifact Registry y foundation de entrega continua.

[Índice y política](../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Sin cambio funcional nuevo documentado en este corte; se agrega trazabilidad histórica.

## Historial confirmado en Git

### 2026-08-08 — `67c190ece54d`

- Cambio registrado: feat(os-08-13): add continuous delivery foundation.
- Alcance en este componente: `deploy/README.md`, `deploy/manifests/dev/release-template.yaml`, `deploy/schemas/release-manifest.schema.json`, `deploy/scripts/validate_release_manifest.py`.

### 2026-08-06 — `1e13b6103547`

- Cambio registrado: feat(cloud-build): add reusable Artifact Registry image pipelines.
- Alcance en este componente: `cloudbuild.yaml`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/gcp/cloudbuild/.gitkeep`.
