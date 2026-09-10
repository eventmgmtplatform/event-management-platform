# Changelog — ServiceNow

Ticketing del Worker: contrato de resultado, clasificación de errores, retries acotados, identidad durable, recuperación de leases, control operacional y reconciliación. Incluye la acción ITSM consumida por CACF.

[Índice y política](../changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: RESOLVE_TICKET toma estado/código explícitos, valida ticketNumber/sysId y confirma por GET; un 2xx sin estado confirmado falla. Reconciliación de resolución incierta sólo consulta, sin repetir PATCH. ESS etiqueta RESOLVED, nunca CLOSED por transporte. El perfil sintético debe sustituirse por contrato validado de cada instancia.

- Mocks y suites referenciados desde testing; no se ha añadido aquí una política nueva de resolución/cierre de ticket por clear.

## Historial confirmado en Git

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowAutomationActionProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowHttpInvoker.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowLookupClient.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowAutomationActionProcessorTest.java`.

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowCommandProcessor.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowCommandProcessorTest.java`.

### 2026-09-04 — `a20c7c0a71c1`

- Cambio registrado: feat(servicenow): add persistent operational control.
- Alcance en este componente: `infrastructure/postgres/init/006-integration-worker-control.sql`.

### 2026-09-04 — `74d75b0f4dec`

- Cambio registrado: feat(servicenow): add safe stale command reconciliation.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowLookupClient.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowReconciliationProcessor.java`, `infrastructure/mock-integrations/servicenow/mappings/lookup-incident-not-found.json`, `infrastructure/postgres/init/005-integration-command-recovery-lease.sql` y 1 archivo(s) adicional(es).

### 2026-09-03 — `1d383ffbdb58`

- Cambio registrado: fix(servicenow): classify command id collisions.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowErrorClassifier.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowErrorClassifierTest.java`.

### 2026-09-03 — `c3a3da450949`

- Cambio registrado: feat(servicenow): add durable command idempotency.
- Alcance en este componente: `infrastructure/postgres/init/004-integration-command-idempotency.sql`.

### 2026-09-02 — `2c1d77275c7a`

- Cambio registrado: feat(servicenow): add controlled retry policy.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowHttpInvoker.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowRetryExecutor.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowRetryExecutorTest.java`.

### 2026-09-02 — `3f5280cdac10`

- Cambio registrado: feat(servicenow): classify permanent and retryable failures.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowErrorClassifier.java`, `services/integration-worker/src/test/java/com/eventmanagement/integration/ServiceNowErrorClassifierTest.java`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/mock-integrations/servicenow/mappings/create-incident.json`.
