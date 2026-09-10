# Changelog — Operación / scripts

Controles locales, operación de servicios, scripts de despliegue/recuperación, publicación Git y certificación.

[Índice y política](../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- CLI Kafka integrada en emctl: consultas, detección de drift y preparación/instalación
  de paquetes independientes con integridad e identidad persistente.

- Certificaciones movidas a testing con enlaces de compatibilidad; referencias actualizadas. El workspace incorpora operación/certificación y despliegue de lifecycle ESS; pendientes de commit.

## Historial confirmado en Git

### 2026-09-09 — `887baeaf51f1`

- Cambio registrado: feat(processor): add independent aiops CRUD and mock provider.
- Alcance en este componente: `scripts/event-processor-deploy.py`, `scripts/eventmanagement-services.sh`, `scripts/processor-aiops-certification.py`.

### 2026-09-09 — `068fae1be8ca`

- Cambio registrado: feat(processor): persist correlation and group command decisions atomically.
- Alcance en este componente: `scripts/event-processor-deploy.py`, `scripts/processor-rest-certification.py`.

### 2026-09-09 — `4ed49de5bccc`

- Cambio registrado: feat(processor): integrate typed enrichment and versioned local inventory.
- Alcance en este componente: `scripts/processor-rest-certification.py`.

### 2026-09-09 — `d06fd735ff02`

- Cambio registrado: feat(processor): add functional REST administration and versioned blackouts.
- Alcance en este componente: `scripts/event-processor-deploy.py`, `scripts/processor-rest-certification.py`.

### 2026-09-09 — `a8d141c4e806`

- Cambio registrado: build(event-processor): deploy versioned rules and verify configuration recovery.
- Alcance en este componente: `scripts/event-processor-deploy.py`, `scripts/event-processor-recovery.py`.

### 2026-09-09 — `54aaa8de1d1e`

- Cambio registrado: feat(event-processor): persist output retries and verify recovery.
- Alcance en este componente: `scripts/event-processor-dependency-recovery.py`, `scripts/event-processor-recovery.py`.

### 2026-09-09 — `baf778df7160`

- Cambio registrado: feat(event-processor): replace enrichment with durable processing foundation.
- Alcance en este componente: `scripts/event-processor-certification.py`, `scripts/eventmanagement-services.sh`, `scripts/eventmanagement-test.py`.

### 2026-09-09 — `3e78b3a6ec5e`

- Cambio registrado: chore(git): consolidate pending workstreams for main.
- Alcance en este componente: `scripts/eventmanagement-services.sh`, `scripts/eventmanagement-test.py`.

### 2026-09-08 — `1c3ede3e9d9a`

- Cambio registrado: feat: add ecosystem service administration and master tests.
- Alcance en este componente: `scripts/eventmanagement-services.sh`, `scripts/eventmanagement-test.py`.

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `scripts/cacf-local-certification.py`.

### 2026-09-07 — `006543a7932d`

- Cambio registrado: feat(os-00-04): establish Git governance baseline.
- Alcance en este componente: `scripts/git/publication-preflight.sh`, `scripts/git/repository-audit.sh`.

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `SN-UI-03.1-console-foundation-preflight.sh`, `SN-UI-03.10R-pkc-inventory-certification.sh`, `SN-UI-03.11-controlled-commit.sh`, `SN-UI-03.12-controlled-push.sh` y 9 archivo(s) adicional(es).

### 2026-08-13 — `cff1e46e3a28`

- Cambio registrado: feat(os-01-01): add enrichment engine foundation.
- Alcance en este componente: `scripts/eventmanagement-services.sh`.

### 2026-08-12 — `f8b4a43fdd51`

- Cambio registrado: feat(os-01-01): add safe local runtime controls.
- Alcance en este componente: `scripts/eventmanagement-services.sh`.
