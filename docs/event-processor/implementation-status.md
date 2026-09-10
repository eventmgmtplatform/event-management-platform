# Alcance y trazabilidad incremental

Rama: feature/os-06-core-event-processor. Estado actual resumido en minimum-v1-checklist.md.
Las secciones fechadas siguientes describen incrementos históricos; sus pendientes pueden haber sido resueltos después.
Fuente: OS_02_EVENT_PROCESSOR_CODEX_MASTER_PROMPT_v1.0.0.md suministrado por el usuario.
ADR-001 confirmado por el usuario el 2026-09-09. No es certificación de release v1.0.0.

| DA | Implementación observada en esta etapa | Estado del DA completo |
|---|---|---|
| 01 | Dominio independiente, contexto inmutable, puertos process/simulate, adaptador de contrato y transporte | PENDING: puertos administrativos y modelos de capacidades restantes |
| 02 | 12 etapas deterministas, directivas, simulación por mismo pipeline, entrada durable | PENDING: algoritmos y escenarios funcionales posteriores |
| 03 | Evidencia/outbox transaccionales, replay concurrente, reintentos persistentes y orden de filas por topic/key | PENDING: configuración especializada y proyección/rebuild; registro POLICY implementado |
| 04 | DSL tipada POLICY, validación y administración REST versionada | PENDING: capacidades especializadas y administración pública autorizada |
| 05 | EnrichmentResult tipado, InventoryPort/local versionado, procedencia/conflictos y evaluación policy | PENDING: fuentes externas, SearchPort y catálogo ampliado |
| 06 | eventKey anterior e identidad de replay conservadas; no nueva autoridad lifecycle | PENDING: StatePort/atomicidad y política de identidad/occurrences |
| 07 | Blackouts y auto-suppression local versionados, reloj, scope/estado y evidencia por match | PENDING: recurrencia, selectores adicionales y sincronización externa |
| 08 | ATTRIBUTE/GROUP durable, ciclos acotados, concurrencia/rollback y simulación secuencial | PENDING: otras estrategias/relaciones y escalamiento |
| 09 | Routing tipado y CREATE_TICKET por grupo, ledger inmutable/outbox atómico | PENDING: otras operaciones, perfiles y ciclos sin correlación; no hay rutas de cliente activas |
| 11 | REST de reglas, simulación y explain; alias legado conservado | PENDING: APIs especializadas; identidad/RBAC diferidos |
| 12 | Health DB/Kafka y evidencia durable; sin payloads inválidos en logs/DLQ | PENDING: métricas/tracing/explain autorizado |
| 13 | Tests sintéticos etiquetados; no porcentaje de paridad | PENDING: fixtures históricos y ejecución; fuentes legacy/schema recibidos |
| 14 | Build/Compose, continuidad de grupo, fresh/upgrade, backup/restore aislado y caída real de PostgreSQL | PENDING: matriz completa de fallos, DR integral/rollback de imagen ejecutado y RPO/RTO |
| 16 | Evidencia de pruebas de esta base | PENDING: no certificación final ni producción |
| 17 | REST sin autenticación; partición funcional por tenant declarado | DEFERRED: identidad/RBAC por decisión del usuario; ver Defect Prevention |
| 18 | ADR, runbook, matriz, evidencias separadas de código | PENDING: cierre de todos los gates |

DA-10 y DA-15: NOT_APPLICABLE al alcance confirmado; no reconstruidos.
Los schemas de diseño ya fueron recibidos; disponibilidad documental no implica implementación ni certificación.
No hay afirmaciones de paridad legacy, GKE, proveedor real, disaster recovery o production readiness.

## Histórico: configuración y reglas, 2026-09-09

Implementados compilador tipado POLICY, validación estructural/semántica, registro
PostgreSQL versionado y evaluación por snapshot. Consultar [contratos y límites](rules-and-contracts.md).
DA-03/04 permanecen PENDING en su alcance completo: administración pública autorizada,
reglas especializadas y gates restantes. El adaptador Worker no activa emisión de comandos.

Validación de este incremento: 44 pruebas Processor y 4 de compatibilidad en Worker PASS,
cero omisiones; empaquetado Quarkus PASS. Migración 011 probada en laboratorio.
Migración 011 y despliegue local ejecutados el 2026-09-09 sobre la implementación b059aaf.
Salud local, replay/reinicio/DLQ y backup/restore con versiones de reglas: PASS.
Cero reglas activas en el ambiente principal. Activación productiva pendiente.

## Histórico: REST y mantenimiento, 2026-09-09

Implementados [REST y blackouts](rest-and-blackouts.md). Seguridad diferida por solicitud
expresa; [pendientes funcionales](defect-prevention.md) identificados sin declarar
el componente completo. Inventory, correlation, auto-suppression y comandos siguen
pendientes. La evidencia de ejecución permanece bajo evidence/os-02-event-processor/.

## Histórico: enrichment e inventory local, 2026-09-09

Implementados planes ENRICHMENT y registros INVENTORY sobre el registro versionado,
snapshot común y hechos tipados consumidos por policy. Simulación conjunta de
configuraciones y salidas normalizadas/DLQ con resultado DA-05. Ver
[contrato y límites](enrichment-and-inventory.md). Correlación, auto-suppression
y comandos permanecen pendientes; no se declara completado el componente.

## Motores conectados, 2026-09-09

Correlación ATTRIBUTE/GROUP, auto-suppression local y routing/CREATE_TICKET por grupo
implementados. Validación: 85 pruebas Processor y 5 de compatibilidad Worker PASS,
sin omisiones; empaquetado PASS. Ver [alcance](correlation-suppression-commands.md).
La seguridad continúa diferida y no se declara completo DA-06 ni el release v1.

## AIOps independiente, 2026-09-09

CRUD REST persistente, revisiones, auditoría inmutable y evaluación explícita por HTTP
contra mock interno. No forma parte de los contratos actuales ni del pipeline automático.
Ver [módulo AIOps](aiops-engine.md) y [cierre mínimo pendiente](minimum-v1-checklist.md).

Validación del incremento AIOps: 91 pruebas Processor PASS (0 fallos/errores/omisiones),
empaquetado PASS. La prueba de arquitectura mantiene dominio/aplicación sin framework.
