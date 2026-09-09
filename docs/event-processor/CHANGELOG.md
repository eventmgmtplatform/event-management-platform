# Event Processor changelog

## Unreleased — revisión funcional GSMA, 2026-09-09

- Mapeo de políticas a las 12 etapas, fuentes de datos con confianza explícita y topología objetivo.
- Prevención de defectos de tenant, ventanas, precedencia y discrepancias de catálogo/contratos.
- Alcance mínimo y 26 especificaciones de comparación en evidencia local; paridad histórica PENDING.
- Actualizado estatus de schemas/fuentes recibidos; sin cambios al servicio ni nueva certificación.

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
