# Changelog — event-gateway

Ingreso de eventos, validación y normalización Zabbix hacia Kafka. La base inicial fue registrada con el repositorio; en agosto se incorporó el contrato nativo Message Bus y sus fixtures/pruebas.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Fuentes JUnit y fixtures trasladados a testing; POM y referencias ajustados. Reportes Maven en evidences. Sin cambio funcional de ingreso por esta reorganización.

## Historial confirmado en Git

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `services/event-gateway/Dockerfile`.

### 2026-08-12 — `3927b0adac16`

- Cambio registrado: test(os-01-01): cover native Zabbix normalization.
- Alcance en este componente: `services/event-gateway/src/test/java/com/eventmanagement/gateway/ZabbixMessageBusNormalizerTest.java`.

### 2026-08-12 — `cc23e2c58a55`

- Cambio registrado: feat(os-01-01): add native Zabbix message bus contract.
- Alcance en este componente: `services/event-gateway/src/main/java/com/eventmanagement/gateway/EventGatewayRoute.java`, `services/event-gateway/src/main/java/com/eventmanagement/gateway/EventValidationProcessor.java`, `services/event-gateway/src/main/java/com/eventmanagement/gateway/ZabbixMessageBusNormalizer.java`, `services/event-gateway/test/events/sdc/zabbix-messagebus-problem.json` y 1 archivo(s) adicional(es).

### 2026-08-05 — `0814802073d0`

- Cambio registrado: docs(services): document normalized build and runtime.
- Alcance en este componente: `services/event-gateway/README.md`.

### 2026-08-04 — `39e6ff6fcafc`

- Cambio registrado: build(services): align Quarkus and Maven versions.
- Alcance en este componente: `services/event-gateway/pom.xml`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `services/event-gateway/src/main/java/com/eventmanagement/gateway/EventGatewayRoute.java`, `services/event-gateway/src/main/java/com/eventmanagement/gateway/EventValidationProcessor.java`, `services/event-gateway/src/main/java/com/eventmanagement/gateway/GatewayExceptionHandler.java`, `services/event-gateway/src/main/resources/application.properties` y 14 archivo(s) adicional(es).
