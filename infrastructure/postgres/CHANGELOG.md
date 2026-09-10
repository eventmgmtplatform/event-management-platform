# Changelog — PostgreSQL / migraciones

Evolución aditiva de esquemas de estado, idempotencia/control del Worker, CACF y reglas/outbox/correlación/AIOps del Processor.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: 020 agrega lifecycle, resultados únicos por resultId y cuarentena por coordenadas Kafka; 021 agrega marcadores de publicación/recovery e índice parcial al ledger Worker. Aplicar después de 009–017 y 004/005/007 respectivamente. Preservar esquema al volver a binarios previos; drenar ciclos antes de revertir. La historia Worker anterior a 021 no se republica masivamente.

- Migración 018: vistas dashboard_read para Events/Ticketing/GNM/CACF y rol de lectura. Validada en PostgreSQL 17 aislado; no aplicada automáticamente al runtime existente.

- Migraciones locales 016-ess-quarantine.sql y 017-ess-lifecycle.sql para cuarentena y lifecycle ESS; pendientes de commit. No inferir aplicación en un ambiente a partir de la existencia del archivo.

## Historial confirmado en Git

### 2026-09-09 — `887baeaf51f1`

- Cambio registrado: feat(processor): add independent aiops CRUD and mock provider.
- Alcance en este componente: `infrastructure/postgres/init/015-processor-aiops.sql`.

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
- Alcance en este componente: `infrastructure/postgres/init/009-event-processor.sql`.

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `infrastructure/postgres/init/008-cacf-core.sql`.

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `infrastructure/postgres/init/007-integration-command-provider-checkpoint.sql`.

### 2026-09-04 — `a20c7c0a71c1`

- Cambio registrado: feat(servicenow): add persistent operational control.
- Alcance en este componente: `infrastructure/postgres/init/006-integration-worker-control.sql`.

### 2026-09-04 — `74d75b0f4dec`

- Cambio registrado: feat(servicenow): add safe stale command reconciliation.
- Alcance en este componente: `infrastructure/postgres/init/005-integration-command-recovery-lease.sql`.

### 2026-09-03 — `c3a3da450949`

- Cambio registrado: feat(servicenow): add durable command idempotency.
- Alcance en este componente: `infrastructure/postgres/init/004-integration-command-idempotency.sql`.

### 2026-08-12 — `2b138dbcbd59`

- Cambio registrado: fix(os-08-14): harden runtime persistence and result idempotency.
- Alcance en este componente: `infrastructure/postgres/init/003-integration-result-idempotency.sql`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/postgres/init/001-initialize-event-management.sql`, `infrastructure/postgres/init/002-event-state.sql`.
