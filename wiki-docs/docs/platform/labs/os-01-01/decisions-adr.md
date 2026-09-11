# OS_01_01 — Decisiones arquitectónicas

## ADR-001 — Conservar compatibilidad v1.0

Decisión: ampliar `/api/v1/events` sin eliminar el contrato anterior. Los eventos nativos usan v1.1 y los anteriores conservan v1.0.

## ADR-002 — Lifecycle mediante Type e InstanceValue

Decisión: `Type` define la acción y `InstanceValue` valida coherencia. `severity` no abre ni cierra el evento.

## ADR-003 — Dos severidades

Decisión: conservar `sourceSeverity` y calcular `effectiveSeverity`. Una recuperación mantiene evidencia del valor de origen y opera con severidad efectiva cero.

## ADR-004 — Identidad tenant-aware

Decisión: usar `CustomerCode:source:AlertKey:Node:InstanceId`; conservar además la identidad heredada. Evita colisiones entre clientes y mantiene trazabilidad.

## ADR-005 — No crear alias SDC/SD2

Decisión: tratarlos como códigos distintos hasta contar con una regla explícita y evidencia autorizada.

## ADR-006 — Payload original inmutable

Decisión: guardar una copia profunda en `originalEvent` para auditoría y reprocesamiento.

## ADR-007 — Orden Kafka por identidad

Decisión: v1.1 publica con `eventKey`; v1.0 conserva `eventId` mientras no tenga identidad canónica.

## ADR-008 — Idempotencia persistente antes del offset

Decisión: reclamar `resultId` y consolidar PostgreSQL, actualizar OpenSearch y sólo entonces confirmar Kafka. Duplicados idénticos son válidos; colisiones se rechazan.

## Riesgos y deuda

- `breakOnFirstError=true` puede crear poison pills; requiere DLQ/retry policy.
- Falta un mecanismo repetible de migraciones para volúmenes PostgreSQL existentes.
- Readiness comprueba PostgreSQL, no una operación funcional Kafka/OpenSearch.
- Falta autenticación productiva del gateway.
- Falta procesamiento posterior de `events.raw`.
