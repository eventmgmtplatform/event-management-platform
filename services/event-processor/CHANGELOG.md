# Changelog — event-processor

Sustituye enrichment-engine en septiembre. Evoluciona de pipeline durable y outbox a reglas tipadas/versionadas, blackouts REST, inventario/enrichment, correlación, supresión, comandos por grupo y AIOps local.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- La administración del Processor y sus integraciones con la WebGUI quedaron
  consolidada y publicada en `06f046f`: reglas/policies, blackouts,
  inventory/enrichment, correlación, auto-suppression, routing, comandos por
  grupo y AIOps local con persistencia PostgreSQL y mock explícito.

- OS_11_01.IMP: perfil lifecycle tipado optativo sobre CREATE_TICKET; estado por tenant/ciclo y consumo de resultados con decisión/comando/outbox atómicos. Bloquea automatizaciones obsoletas, cierres de grupos activos y falsos éxitos. Migración aditiva 020 requerida antes del binario; rutas previas sin lifecycle conservan comportamiento. Reversión: deshabilitar perfiles nuevos y drenar/revisar ciclos, conservando tablas y offsets.

- Testing centralizado y reportes fuera del código. El workspace incorpora
  StateRequestAdapter y su emisión en la unidad transaccional hacia ESS; está
  incluido en el corte publicado, distinto del encadenamiento completo OS_09.

## Historial confirmado en Git

### 2026-09-09 — `887baeaf51f1`

- Cambio registrado: feat(processor): add independent aiops CRUD and mock provider.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/ProcessorWiring.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AiopsMockClient.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AiopsResource.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/postgres/PostgresAiopsConfigurations.java` y 18 archivo(s) adicional(es).

### 2026-09-09 — `068fae1be8ca`

- Cambio registrado: feat(processor): persist correlation and group command decisions atomically.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminResource.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/AcceptedEventProcessor.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/GatewayEventAdapter.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/WorkerCommandAdapter.java` y 42 archivo(s) adicional(es).

### 2026-09-09 — `4ed49de5bccc`

- Cambio registrado: feat(processor): integrate typed enrichment and versioned local inventory.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminResource.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/AcceptedEventProcessor.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/postgres/PostgresRuleStore.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/rules/RuleCompiler.java` y 22 archivo(s) adicional(es).

### 2026-09-09 — `d06fd735ff02`

- Cambio registrado: feat(processor): add functional REST administration and versioned blackouts.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/ProcessorWiring.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminExceptionMapper.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminRequestContext.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminResource.java` y 31 archivo(s) adicional(es).

### 2026-09-09 — `a8d141c4e806`

- Cambio registrado: build(event-processor): deploy versioned rules and verify configuration recovery.
- Alcance en este componente: `docs/event-processor/CHANGELOG.md`, `docs/event-processor/implementation-status.md`, `services/event-processor/README.md`, `services/event-processor/src/test/java/com/eventmanagement/processor/adapters/postgres/RestoreReplayIT.java`.

### 2026-09-09 — `b059aaf8cdbe`

- Cambio registrado: feat(event-processor): add versioned typed policy configuration.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/ProcessorWiring.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/AcceptedEventProcessor.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/WorkerCommandAdapter.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/postgres/PostgresRuleStore.java` y 21 archivo(s) adicional(es).

### 2026-09-09 — `58e45fd30474`

- Alcance en este componente: `docs/event-processor/CHANGELOG.md`, `docs/event-processor/decisions-adr.md`, `docs/event-processor/implementation-status.md`, `docs/event-processor/legacy-mapping.md`.

### 2026-09-09 — `54aaa8de1d1e`

- Cambio registrado: feat(event-processor): persist output retries and verify recovery.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/postgres/OutboxDispatcher.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/observability/ProcessorReadiness.java`, `services/event-processor/src/main/resources/application.properties`, `docs/event-processor/CHANGELOG.md` y 7 archivo(s) adicional(es).

### 2026-09-09 — `baf778df7160`

- Cambio registrado: feat(event-processor): replace enrichment with durable processing foundation.
- Alcance en este componente: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/ProcessorWiring.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/AcceptedEventProcessor.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/kafka/GatewayEventAdapter.java`, `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/postgres/OutboxDispatcher.java` y 25 archivo(s) adicional(es).

## Notas de diseño recuperadas del changelog anterior

Las entradas anteriormente rotuladas Unreleased se correlacionan con los commits de septiembre arriba. No representan releases nuevas. Resultados de ejecución se conservaron en evidences.


- Add independent AIOps configuration CRUD, immutable audit and explicit HTTP assessment via local mock; event/rule contracts unchanged.
- Record minimum local v1 acceptance gaps and defer the real provider adapter.

### Unreleased — local deployment and configuration recovery, 2026-09-09

- Local deployment script preserves prior image and schema backup; applies migration 011 before replacement.
- Replay/restart/DLQ certification gates deployment; image rollback is prepared on failure.
- Recovery tests now verify immutable rule versions, lifecycle history and the restored evaluator.

### Unreleased — versioned policy configuration, 2026-09-09

- Typed POLICY compiler: local JSON Schema, semantic validation, bounded operators and deterministic resolution.
- PostgreSQL immutable versions, tenant scope, lifecycle audit and optimistic activation; one snapshot per event.
- Worker envelope compatibility boundary and sanitized DLQ envelope; no active routing or provider effects.
- Reference-only analysis remains outside version control.

### Unreleased — durable output recovery, 2026-09-09

- Backoff persistente, contador de intentos y error sanitizado; no descarta pendientes.
- Orden entre filas comprometidas de la misma topic/key, con progreso de claves independientes.
- Shutdown deja de reclamar nuevas salidas y readiness exige la migración 010.
- Pruebas PostgreSQL concurrentes, fresh/upgrade, backup/restore y recuperación real tras caída DB.
- Sin cambios al contrato público ni a reglas/lifecycle; no equivale a DR productivo certificado.

### Unreleased — foundation, 2026-09-09

- Sustitución compatible de enrichment-engine en 8082; grupo Kafka conservado.
- Adaptador interno de contratos gateway v1.0/v1.1; sin modificar gateway.
- Pipeline explícito y contexto inmutable; etapas aún no implementadas visibles.
- Evidencia/outbox PostgreSQL transaccionales antes de confirmar Kafka.
- Replay concurrente, colisiones, DLQ sin payload inválido y recuperación de salida.
- Health PostgreSQL/Kafka; sin ejecución directa de proveedores.
- Release v1.0.0 completo sigue PENDING; consultar implementation-status.md.

### 2026-09-09 — REST funcional y blackouts

- Administración versionada, concurrencia optimista, recibos idempotentes e historial transaccional.
- Simulación sin escrituras y consulta de evidencia persistida.
- Blackouts inmediatos/programados con scope exacto, ventana y reloj explícitos, integrados con PolicyEvaluation.
- Identidad y RBAC diferidos por decisión del usuario; próximos motores en Defect Prevention.

### 2026-09-09 — enrichment e inventory local

- Consulta tipada de inventario versionado desde planes ENRICHMENT, en un mismo snapshot.
- Hechos con procedencia, conflictos explícitos y criticalidad por consulta; consumo por policy.
- Simulación conjunta y resultado DA-05 en auditoría/salida; DLQ atribuida a su etapa real.

### 2026-09-09 — correlación, supresión y comandos

- Grupos por atributos, pertenencia/ciclos durables y simulación secuencial.
- Auto-suppression por registro local de mantenimiento/cambio.
- Routing tipado y CREATE_TICKET por grupo con ledger inmutable, sin ejecución de proveedores en Processor.
- Unidad de trabajo atómica para relaciones, evidencia, comandos y outbox; replay previo a reevaluación.
