# Event Processor changelog

## Unreleased — foundation, 2026-09-09

- Sustitución compatible de enrichment-engine en 8082; grupo Kafka conservado.
- Adaptador interno de contratos gateway v1.0/v1.1; sin modificar gateway.
- Pipeline explícito y contexto inmutable; etapas aún no implementadas visibles.
- Evidencia/outbox PostgreSQL transaccionales antes de confirmar Kafka.
- Replay concurrente, colisiones, DLQ sin payload inválido y recuperación de salida.
- Health PostgreSQL/Kafka; sin ejecución directa de proveedores.
- 18 pruebas unitarias/integración aprobadas y certificación de replay/reinicio/DLQ.
- Release v1.0.0 completo sigue PENDING; consultar implementation-status.md.
