# Changelog — integration-worker

Ejecución de comandos y publicación de resultados. ServiceNow incorpora retries, ledger durable y reconciliación; posteriormente se agregan GNM/Everbridge y CACF/NEXT. Los detalles por proveedor están enlazados en el índice de changelogs.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: completa el envelope GNM antes del ledger; agrega RESOLVE_TICKET con política explícita y confirmación GET; permite registry GNM por archivo local. Migración aditiva 021 conserva intención de publicación en el ledger y recupera resultados/claims vencidos mediante replay seguro. No promete exactly-once externo; mantiene reconciliación y revisión de mutaciones inciertas.

- Pruebas y recursos trasladados a testing/services/integration-worker; contratos compartidos y POM apuntan a la nueva ubicación. No se presenta como implementado el encadenamiento OS_09.

## Historial confirmado en Git

### 2026-09-09 — `068fae1be8ca`

- Cambio registrado: feat(processor): persist correlation and group command decisions atomically.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/ProcessorGroupCommandContractTest.java`.

### 2026-09-09 — `b059aaf8cdbe`

- Cambio registrado: feat(event-processor): add versioned typed policy configuration.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandProcessorTest.java`.

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/CamelServiceNowHttpInvoker.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/CamelServiceNowLookupClient.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationProvider.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowAutomationActionProcessor.java` y 21 archivo(s) adicional(es).

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `services/integration-worker/Dockerfile`.

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/CamelGnmHttpInvoker.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/EverbridgeGnmIncidentLookupClient.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/EverbridgeProviderContext.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/EverbridgeVariableCatalog.java` y 58 archivo(s) adicional(es).

### 2026-09-05 — `b2475908289a`

- Cambio registrado: feat(servicenow): add pull restart recovery coordinator.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperationalControl.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperationalState.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/JdbcIntegrationCommandLedger.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/PullRestartCoordinator.java` y 4 archivo(s) adicional(es).

### 2026-09-04 — `a20c7c0a71c1`

- Cambio registrado: feat(servicenow): add persistent operational control.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperatingMode.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperationalControl.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperationalControlResource.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationOperationalState.java` y 5 archivo(s) adicional(es).

### 2026-09-04 — `74d75b0f4dec`

- Cambio registrado: feat(servicenow): add safe stale command reconciliation.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/CamelServiceNowLookupClient.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandClaimProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandCompletionProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandLedger.java` y 9 archivo(s) adicional(es).

### 2026-09-03 — `1d383ffbdb58`

- Cambio registrado: fix(servicenow): classify command id collisions.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/CommandIdCollisionException.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/JdbcIntegrationCommandLedger.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowErrorClassifier.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java` y 1 archivo(s) adicional(es).

### 2026-09-03 — `c3a3da450949`

- Cambio registrado: feat(servicenow): add durable command idempotency.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandClaimProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandCompletionProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandLedger.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationWorkerReadinessCheck.java` y 7 archivo(s) adicional(es).

### 2026-09-02 — `2c1d77275c7a`

- Cambio registrado: feat(servicenow): add controlled retry policy.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/CamelServiceNowHttpInvoker.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationFailureProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationResultProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowHttpInvoker.java` y 6 archivo(s) adicional(es).

### 2026-09-02 — `3f5280cdac10`

- Cambio registrado: feat(servicenow): classify permanent and retryable failures.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationFailureProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowErrorClassifier.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowErrorClassifierTest.java`.

### 2026-09-02 — `3bc9cdf2e1b9`

- Cambio registrado: feat(servicenow): add Kafka-aware worker readiness.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationWorkerReadinessCheck.java`, `services/integration-worker/src/main/resources/application.properties`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationWorkerReadinessCheckTest.java`.

### 2026-09-02 — `13bb979928a9`

- Cambio registrado: feat(servicenow): add compatible result contract v1.1.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationFailureProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationResultProcessor.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandProcessorTest.java` y 2 archivo(s) adicional(es).

### 2026-09-02 — `fb8f050b681d`

- Cambio registrado: fix(servicenow): preserve event key in integration results.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationKafkaKeyProcessor.java`, `services/integration-worker/src/main/resources/routes/integration-worker.xml`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationKafkaKeyProcessorTest.java`.

### 2026-08-05 — `0814802073d0`

- Cambio registrado: docs(services): document normalized build and runtime.
- Alcance en este componente: `services/integration-worker/README.md`.

### 2026-08-05 — `8f3a8e5528cd`

- Cambio registrado: build(services): normalize container build layout.
- Alcance en este componente: `services/integration-worker/.dockerignore`, `services/integration-worker/Dockerfile`.

### 2026-08-04 — `39e6ff6fcafc`

- Cambio registrado: build(services): align Quarkus and Maven versions.
- Alcance en este componente: `services/integration-worker/pom.xml`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationCommandProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationFailureProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/IntegrationResultProcessor.java`, `services/integration-worker/src/main/resources/application.properties` y 6 archivo(s) adicional(es).
