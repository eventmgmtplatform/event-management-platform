# Decisiones Event Processor

## ADR-001 — sustitución compatible, aceptada 2026-09-09

El usuario confirmó evolucionar enrichment-engine a event-processor en 8082,
reutilizando su build Quarkus/Camel y transporte Kafka. Un solo consumidor activo
sustituye al anterior. Se conserva el grupo enrichment-engine para continuar los
offsets; no se resetean ni se ejecutan ambos productores de events.normalized.

Gateway permanece sin cambios. El adaptador interpreta v1.1 OPEN como PROBLEM,
CLOSE como OK y effectiveSeverity como severidad interna 0..5. v1.0 usa alert.status
y alert.severity. Nunca se infiere RESOLVED a partir de CLOSE. El envelope original
se conserva en la salida normalized; /api/v1/enrichment se mantiene como alias de
compatibilidad mientras /health/ready es el endpoint de disponibilidad.

## ADR-002 — autoridad y entrega de la base

Event State Service conserva event_management.event_state y sus resultados de
integración. Processor no crea una segunda autoridad de lifecycle o correlación.
La base agrega sólo evidencia de procesamiento y outbox de salida normalizada/DLQ
en tablas propias, transacción PostgreSQL antes de confirmar Kafka. La publicación
es at-least-once: un fallo después de publicar antes de marcar puede duplicar
la entrega con el mismo identificador. No se afirma exactly-once.

Los algoritmos pendientes de implementar a partir de los schemas DA permanecen explícitos
como PENDING, sin defaults de negocio inventados. DA-10 y DA-15 se excluyen según
el documento. El master describe 16 DA; no prueba su implementación.

## Contratos de diseño recibidos

BASELINE, DA aplicables y schemas están disponibles. La compatibilidad implementada
se describe en [rules-and-contracts.md](rules-and-contracts.md). Las fuentes auxiliares
de análisis permanecen fuera del repositorio. Los fixtures históricos con configuración
y resultados esperados siguen pendientes. IdP, roles productivos, SLO y RPO/RTO no se inventan.

## ADR-003 — recuperación de salidas, 2026-09-09

El orden cubre filas ya comprometidas según dispatch_sequence para la misma
pareja topic/key. No pretende resolver timestamps del origen ni lifecycle fuera
de orden. Una fila aplazada bloquea sus sucesoras; otras claves siguen disponibles.
Se conserva bloqueo PostgreSQL hasta publicar/registrar resultado. Un savepoint
permite registrar el fallo sin liberar prematuramente la fila. Crash/conexión DB
perdida antes de commit puede perder la metadata del intento, nunca el intent
comprometido; se admite entrega duplicada con identidad estable.

La migración aditiva 010 permite escritores anteriores mediante defaults. Readiness
de la imagen nueva exige las columnas nuevas. El backup/restore probado cubre sólo
el schema event_processor en bases aisladas; la recuperación completa de plataforma
y los objetivos RPO/RTO siguen pendientes.
