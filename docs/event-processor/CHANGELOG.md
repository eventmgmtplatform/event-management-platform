# Event Processor changelog

## Unreleased — local deployment and configuration recovery, 2026-09-09

- Local deployment script preserves prior image and schema backup; applies migration 011 before replacement.
- Replay/restart/DLQ certification gates deployment; image rollback is prepared on failure.
- Recovery tests now verify immutable rule versions, lifecycle history and the restored evaluator.
- Local health, runtime certification and isolated configuration recovery passed; no customer rules activated.

## Unreleased — versioned policy configuration, 2026-09-09

- Typed POLICY compiler: local JSON Schema, semantic validation, bounded operators and deterministic resolution.
- PostgreSQL immutable versions, tenant scope, lifecycle audit and optimistic activation; one snapshot per event.
- Worker envelope compatibility boundary and sanitized DLQ envelope; no active routing or provider effects.
- Reference-only analysis remains outside version control.

## Unreleased — durable output recovery, 2026-09-09

- Backoff persistente, contador de intentos y error sanitizado; no descarta pendientes.
- Orden entre filas comprometidas de la misma topic/key, con progreso de claves independientes.
- Shutdown deja de reclamar nuevas salidas y readiness exige la migración 010.
- Pruebas PostgreSQL concurrentes, fresh/upgrade, backup/restore y recuperación real tras caída DB.
- Sin cambios al contrato público ni a reglas/lifecycle; no equivale a DR productivo certificado.

## Unreleased — foundation, 2026-09-09

- Sustitución compatible de enrichment-engine en 8082; grupo Kafka conservado.
- Adaptador interno de contratos gateway v1.0/v1.1; sin modificar gateway.
- Pipeline explícito y contexto inmutable; etapas aún no implementadas visibles.
- Evidencia/outbox PostgreSQL transaccionales antes de confirmar Kafka.
- Replay concurrente, colisiones, DLQ sin payload inválido y recuperación de salida.
- Health PostgreSQL/Kafka; sin ejecución directa de proveedores.
- 18 pruebas unitarias/integración aprobadas y certificación de replay/reinicio/DLQ.
- Release v1.0.0 completo sigue PENDING; consultar implementation-status.md.

## 2026-09-09 — REST funcional y blackouts

- Administración versionada, concurrencia optimista, recibos idempotentes e historial transaccional.
- Simulación sin escrituras y consulta de evidencia persistida.
- Blackouts inmediatos/programados con scope exacto, ventana y reloj explícitos, integrados con PolicyEvaluation.
- Identidad y RBAC diferidos por decisión del usuario; próximos motores en Defect Prevention.

## 2026-09-09 — enrichment e inventory local

- Consulta tipada de inventario versionado desde planes ENRICHMENT, en un mismo snapshot.
- Hechos con procedencia, conflictos explícitos y criticalidad por consulta; consumo por policy.
- Simulación conjunta y resultado DA-05 en auditoría/salida; DLQ atribuida a su etapa real.
