# Changelog — event-state-service

Consolidación de resultados de integración en PostgreSQL y proyección OpenSearch. El historial incluye endurecimiento de persistencia e idempotencia de resultados.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- API administrativa de lectura: estado/historial por tenant con tokens vinculados;
  resumen global de cuarentena con permiso separado, sin payloads. CLI admin y
  plantilla 1.2.0. Despliegue sólo ESS aprobado con 34 pruebas y 10 checks de servicio.


- Despliegue compartido coordinado Processor/Worker/ESS: PASS con 29 pruebas ESS
  y 9 checks de servicio, incluido reinicio. Corrige detección de lag en
  particiones vacías; rollback de las tres imágenes comprobado en el primer intento.


- Certificación: plantilla 1.1.0 exige lifecycle OPEN/CLOSE/reapertura y replay/stale.
  Preflight impide crear fixtures sin tablas/consumer requeridos y bloquea Maven
  sobre un target montado en runtime activo. Documentación distingue baseline
  compartida y evidencia OS_11; UC-001 repetido con PASS tras reorganización.


- OS_11_01.IMP: proyecta GNM OPEN/CLOSED confirmado, ServiceNow RESOLVED confirmado y outcome CACF. Conserva confirmaciones terminales ante aperturas/notas tardías del mismo agregado. Sin nueva migración ESS; mantiene agregados separados para fuente y situación y la proyección existente.

- Trabajo local ESS: validación de contratos, cuarentena de entradas inválidas, transacciones/aislamiento, solicitudes de estado y transiciones de lifecycle. Migraciones 016/017 y certificaciones asociadas aún sin commit. Pruebas en testing/services/event-state-service y reportes en evidences.

## Historial confirmado en Git

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `services/event-state-service/Dockerfile`.

### 2026-08-12 — `2b138dbcbd59`

- Cambio registrado: fix(os-08-14): harden runtime persistence and result idempotency.
- Alcance en este componente: `services/event-state-service/src/main/java/com/eventmanagement/state/EventStateRepository.java`, `services/event-state-service/src/main/java/com/eventmanagement/state/IntegrationResultStateProcessor.java`, `services/event-state-service/src/main/resources/routes/integration-results-route.xml`.

### 2026-08-05 — `0814802073d0`

- Cambio registrado: docs(services): document normalized build and runtime.
- Alcance en este componente: `services/event-state-service/README.md`.

### 2026-08-05 — `8f3a8e5528cd`

- Cambio registrado: build(services): normalize container build layout.
- Alcance en este componente: `services/event-state-service/.dockerignore`, `services/event-state-service/Dockerfile`.

### 2026-08-04 — `39e6ff6fcafc`

- Cambio registrado: build(services): align Quarkus and Maven versions.
- Alcance en este componente: `services/event-state-service/pom.xml`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `services/event-state-service/src/main/java/com/eventmanagement/state/ConsolidatedEventState.java`, `services/event-state-service/src/main/java/com/eventmanagement/state/EventStateRepository.java`, `services/event-state-service/src/main/java/com/eventmanagement/state/IntegrationResultStateProcessor.java`, `services/event-state-service/src/main/java/com/eventmanagement/state/OpenSearchStateClient.java` y 6 archivo(s) adicional(es).
