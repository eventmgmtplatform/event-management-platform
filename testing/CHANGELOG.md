# Changelog — Testing

Historial de pruebas desde los fixtures iniciales, JUnit Gateway, contratos Worker y suites Processor hasta la centralización actual.

[Índice y política](../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: UC-001 ejecutable por fronteras públicas con mocks propios, identidades cruzadas, orden, duplicados y confirmaciones; laboratorio aislado y certificación de reinicio entre NEXT SUBMITTED/ACK. Regresiones SQL del coordinador, publicación Worker y terminales ESS; validación de resolución/GNM. Se conserva UC-002 y se explicita su alcance. Evidencias únicamente en evidences; migraciones 020/021 en entorno y fixtures de DB.

- Base única testing, runner, catálogo/plantilla de casos, blackout y escenario CACF exitoso. El bloqueo inicial de UC-001 se sustituye por el escenario OS_11 descrito arriba; reportes en evidences y rutas anteriores con compatibilidad.

### Detalle de la centralización


#### Base centralizada

- Fuentes y recursos JUnit de Gateway, Processor, Worker y ESS trasladados a
  `testing/services/`; Maven conserva la ejecución desde cada servicio.
- Fixtures Gateway consolidados en `testing/fixtures/events/`, eliminando la copia
  idéntica de `test/events/`.
- Mocks WireMock trasladados a `testing/mocks/`; referencias de Compose actualizadas.
- Ambiente CACF trasladado a `testing/environments/cacf.compose.yml` con rutas
  relativas ajustadas para volúmenes y build.
- Certificaciones Python trasladadas a `testing/certifications/`, con enlaces de
  compatibilidad desde `scripts/`; certificaciones históricas de consola en
  `testing/legacy/console/`. Despliegue y publicación permanecen como operaciones.
- Pruebas Terraform del módulo común en `testing/terraform/project-common/`.

- Incorporadas las pruebas Node de snapshots administrativos de consola, con
  enlace de compatibilidad y ejecución `python3 testing/run.py console`.

#### Casos y ejecución

- Runner único con selección de servicio, reportes por ejecución, conteo de
  omisiones y códigos diferenciados para fallo y bloqueo.
- Catálogo inicial de 14 casos con alcance y estado de implementación; plantilla
  para ampliar cobertura sin mezclar especificaciones y resultados.
- UC-001 documenta fatal → ticket → GNM con ticket → CACF REMEDIATED → clear.
  Su ejecución queda bloqueada explícitamente hasta disponer del encadenamiento
  automático y cierres confirmados; las pruebas de componentes no lo aprueban.
- UC-002 ejercita registro/activación de blackout, routing elegible, evento fatal,
  clear, scope y desactivación; inspecciona decisiones persistidas y ausencia de
  comandos de integración, con limpieza de reglas propias.
- UC-003 agrega ejecución enfocada en respuesta CACF REMEDIATED y callback duplicado
  contra el ambiente aislado existente.
- Pruebas del runner verifican descubrimiento Maven, catálogo, polling y que los
  bloqueos o aserciones incumplidas no se presenten como éxito.

#### Evidencias y compatibilidad

- Nuevos resultados en `evidences/`, excluido de Git. Maven dirige sus reportes a
  ese directorio; el runner usa UTC + UUID para evitar sobrescrituras.
- `evidence/` conserva el histórico previo. Resultados CACF que estaban en
  documentación se conservan en `evidences/testing/imported/`.
- Actualizadas referencias operativas/documentales a las rutas nuevas.
- Este changelog describe cambios de código y contrato; conteos, fallos, logs y
  resultados de ejecuciones pertenecen exclusivamente a `evidences/`.


## Historial confirmado en Git

### 2026-09-09 — `887baeaf51f1`

- Cambio registrado: feat(processor): add independent aiops CRUD and mock provider.
- Alcance en este componente: `infrastructure/mock-integrations/aiops/mappings/assess.json`, `scripts/processor-aiops-certification.py`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiEnvironment.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AiopsApiEnvironment.java` y 2 archivo(s) adicional(es).

### 2026-09-09 — `068fae1be8ca`

- Cambio registrado: feat(processor): persist correlation and group command decisions atomically.
- Alcance en este componente: `scripts/processor-rest-certification.py`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiEnvironment.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiIT.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/WorkerContractTest.java` y 6 archivo(s) adicional(es).

### 2026-09-09 — `4ed49de5bccc`

- Cambio registrado: feat(processor): integrate typed enrichment and versioned local inventory.
- Alcance en este componente: `scripts/processor-rest-certification.py`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiIT.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/EnrichmentDeliveryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/rules/EnrichmentTest.java`.

### 2026-09-09 — `d06fd735ff02`

- Cambio registrado: feat(processor): add functional REST administration and versioned blackouts.
- Alcance en este componente: `scripts/processor-rest-certification.py`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiEnvironment.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/http/AdminApiIT.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/rules/BlackoutTest.java` y 2 archivo(s) adicional(es).

### 2026-09-09 — `a8d141c4e806`

- Cambio registrado: build(event-processor): deploy versioned rules and verify configuration recovery.
- Alcance en este componente: `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/RestoreReplayIT.java`.

### 2026-09-09 — `b059aaf8cdbe`

- Cambio registrado: feat(event-processor): add versioned typed policy configuration.
- Alcance en este componente: `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/DurableBoundaryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/WorkerContractTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/RuleRegistryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/rules/RuleCompilerTest.java` y 2 archivo(s) adicional(es).

### 2026-09-09 — `54aaa8de1d1e`

- Cambio registrado: feat(event-processor): persist output retries and verify recovery.
- Alcance en este componente: `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/OutboxRecoveryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/PostgresBoundaryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/RestoreReplayIT.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/RetryDelayTest.java`.

### 2026-09-09 — `baf778df7160`

- Cambio registrado: feat(event-processor): replace enrichment with durable processing foundation.
- Alcance en este componente: `scripts/event-processor-certification.py`, `scripts/eventmanagement-test.py`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/DurableBoundaryTest.java`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/kafka/GatewayAdapterTest.java` y 2 archivo(s) adicional(es).

### 2026-09-09 — `3e78b3a6ec5e`

- Cambio registrado: chore(git): consolidate pending workstreams for main.
- Alcance en este componente: `scripts/eventmanagement-test.py`.

### 2026-09-08 — `1c3ede3e9d9a`

- Cambio registrado: feat: add ecosystem service administration and master tests.
- Alcance en este componente: `scripts/eventmanagement-test.py`.

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `infrastructure/docker-compose.cacf-test.yml`, `infrastructure/mock-integrations/cacf-servicenow/mappings/lookup.json`, `infrastructure/mock-integrations/cacf-servicenow/mappings/update.json`, `infrastructure/mock-integrations/next/mappings/create.json` y 8 archivo(s) adicional(es).

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `SN-02.9E-owner-guarded-package/scripts/02-source-certification.sh`, `SN-UI-03.10R-pkc-inventory-certification.sh`, `SN-UI-03.2R-corrected-scaffold-certification.sh`, `SN-UI-03.2R2-final-scaffold-certification.sh` y 4 archivo(s) adicional(es).

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `infrastructure/mock-integrations/gnm/__files/close-success.json`, `infrastructure/mock-integrations/gnm/__files/duplicate-close.json`, `infrastructure/mock-integrations/gnm/__files/incident-closed.json`, `infrastructure/mock-integrations/gnm/__files/incident-open.json` y 34 archivo(s) adicional(es).

### 2026-09-05 — `b2475908289a`

- Cambio registrado: feat(servicenow): add pull restart recovery coordinator.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationOperationalStateTest.java`.

### 2026-09-04 — `a20c7c0a71c1`

- Cambio registrado: feat(servicenow): add persistent operational control.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationOperationalStateTest.java`.

### 2026-09-04 — `74d75b0f4dec`

- Cambio registrado: feat(servicenow): add safe stale command reconciliation.
- Alcance en este componente: `infrastructure/mock-integrations/servicenow/mappings/lookup-incident-not-found.json`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandClaimProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandCompletionProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/OwnerGuardedLedgerContractTest.java` y 1 archivo(s) adicional(es).

### 2026-09-03 — `1d383ffbdb58`

- Cambio registrado: fix(servicenow): classify command id collisions.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowErrorClassifierTest.java`.

### 2026-09-03 — `c3a3da450949`

- Cambio registrado: feat(servicenow): add durable command idempotency.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandClaimProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandCompletionProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationWorkerReadinessCheckTest.java`.

### 2026-09-02 — `2c1d77275c7a`

- Cambio registrado: feat(servicenow): add controlled retry policy.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationResultProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowRetryExecutorTest.java`.

### 2026-09-02 — `3f5280cdac10`

- Cambio registrado: feat(servicenow): classify permanent and retryable failures.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowErrorClassifierTest.java`.

### 2026-09-02 — `3bc9cdf2e1b9`

- Cambio registrado: feat(servicenow): add Kafka-aware worker readiness.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationWorkerReadinessCheckTest.java`.

### 2026-09-02 — `13bb979928a9`

- Cambio registrado: feat(servicenow): add compatible result contract v1.1.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationCommandProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationFailureProcessorTest.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationResultProcessorTest.java`.

### 2026-09-02 — `fb8f050b681d`

- Cambio registrado: fix(servicenow): preserve event key in integration results.
- Alcance en este componente: `services/integration-worker/src/test/java/com/eventmanagement/integration/IntegrationKafkaKeyProcessorTest.java`.

### 2026-08-12 — `3927b0adac16`

- Cambio registrado: test(os-01-01): cover native Zabbix normalization.
- Alcance en este componente: `services/event-gateway/src/test/java/com/eventmanagement/gateway/ZabbixMessageBusNormalizerTest.java`.

### 2026-08-12 — `cc23e2c58a55`

- Cambio registrado: feat(os-01-01): add native Zabbix message bus contract.
- Alcance en este componente: `services/event-gateway/test/events/sdc/zabbix-messagebus-problem.json`, `services/event-gateway/test/events/sdc/zabbix-messagebus-recovery.json`.

### 2026-08-04 — `94242ef73f5a`

- Cambio registrado: chore(terraform): add OS_08_10 operational scripts.
- Alcance en este componente: `infrastructure/gcp/terraform/scripts/os-08-10/04-final-certification.sh`.

### 2026-08-01 — `7c0e54dce5fc`

- Cambio registrado: feat(terraform): add project-common conventions module.
- Alcance en este componente: `infrastructure/terraform/modules/common/project-common/tests/project_common.tftest.hcl`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/mock-integrations/servicenow/mappings/create-incident.json`, `services/event-gateway/test/events/zabbix-ok.json`, `services/event-gateway/test/events/zabbix-problem.json`, `test/events/zabbix-ok.json` y 1 archivo(s) adicional(es).
