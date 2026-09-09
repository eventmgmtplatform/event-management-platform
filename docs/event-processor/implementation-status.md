# Alcance y trazabilidad incremental

Base: 425ecd3; rama feature/os-06-core-event-processor.
Fuente: OS_02_EVENT_PROCESSOR_CODEX_MASTER_PROMPT_v1.0.0.md suministrado por el usuario.
ADR-001 confirmado por el usuario el 2026-09-09. No es certificación de release v1.0.0.

| DA | Implementación observada en esta etapa | Estado del DA completo |
|---|---|---|
| 01 | Dominio independiente, contexto inmutable, puertos process/simulate, adaptador de contrato y transporte | PENDING: puertos administrativos y modelos de capacidades restantes |
| 02 | 12 etapas deterministas, directivas, simulación por mismo pipeline, entrada durable | PENDING: algoritmos y escenarios funcionales posteriores |
| 03 | Evidencia/outbox transaccionales, replay concurrente, reintentos persistentes y orden de filas por topic/key | PENDING: configuración especializada y proyección/rebuild; registro POLICY implementado |
| 04 | DSL tipada POLICY, validación y activación interna versionada | PENDING: capacidades especializadas y administración pública autorizada |
| 05 | Contrato de enrichment anterior conservado con PENDING_RULES | PENDING: implementar enrichment-result y puertos; fuentes de diseño recibidas |
| 06 | eventKey anterior e identidad de replay conservadas; no nueva autoridad lifecycle | PENDING: StatePort/atomicidad y política de identidad/occurrences |
| 07 | Sólo semántica de directiva probada | PENDING: implementar schemas suministrados, recurrencia e importación maintenance |
| 08 | Sin relaciones ni búsquedas de candidatos simuladas | PENDING: implementar schemas suministrados y coordinación de ownership |
| 09 | No se emiten comandos: no hay rutas activas | PENDING: routing/payloads/idempotencia por ciclo y target |
| 11 | Alias de salud legado; sin endpoints administrativos públicos | PENDING: implementar contratos suministrados y autorización |
| 12 | Health DB/Kafka y evidencia durable; sin payloads inválidos en logs/DLQ | PENDING: métricas/tracing/explain autorizado |
| 13 | Tests sintéticos etiquetados; no porcentaje de paridad | PENDING: fixtures históricos y ejecución; fuentes legacy/schema recibidos |
| 14 | Build/Compose, continuidad de grupo, fresh/upgrade, backup/restore aislado y caída real de PostgreSQL | PENDING: matriz completa de fallos, DR integral/rollback de imagen ejecutado y RPO/RTO |
| 16 | Evidencia de pruebas de esta base | PENDING: no certificación final ni producción |
| 17 | Sin APIs de mutación ni proveedor en processor; credenciales sólo en variables runtime | PENDING: principal/RBAC/tenancy y scans completos |
| 18 | ADR, runbook, matriz, evidencias separadas de código | PENDING: cierre de todos los gates |

DA-10 y DA-15: NOT_APPLICABLE al alcance confirmado; no reconstruidos.
Los schemas de diseño ya fueron recibidos; disponibilidad documental no implica implementación ni certificación.
No hay afirmaciones de paridad legacy, GKE, proveedor real, disaster recovery o production readiness.

## Configuración y reglas, 2026-09-09

Implementados compilador tipado POLICY, validación estructural/semántica, registro
PostgreSQL versionado y evaluación por snapshot. Consultar [contratos y límites](rules-and-contracts.md).
DA-03/04 permanecen PENDING en su alcance completo: administración pública autorizada,
reglas especializadas y gates restantes. El adaptador Worker no activa emisión de comandos.

Validación de este incremento: 44 pruebas Processor y 4 de compatibilidad en Worker PASS,
cero omisiones; empaquetado Quarkus PASS. Migración 011 probada en laboratorio.
Migración 011 y despliegue local ejecutados el 2026-09-09 sobre la implementación b059aaf.
Salud local, replay/reinicio/DLQ y backup/restore con versiones de reglas: PASS.
Cero reglas activas en el ambiente principal. Activación productiva pendiente.
