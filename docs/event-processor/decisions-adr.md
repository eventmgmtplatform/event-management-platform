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

Los algoritmos dependientes de schemas DA no suministrados permanecen explícitos
como PENDING, sin defaults de negocio inventados. DA-10 y DA-15 se excluyen según
el documento. El master describe 16 DA; no prueba su implementación.

## Artefactos externos pendientes

No se localizaron schemas congelados rule-v1, enrichment-result-v1, suppression,
blackout, correlation y parity-fixture, ni políticas GSMA nombradas. No se
reconstruirán como si fueran las fuentes originales. Los endpoints de mutación
administrativa permanecerán sin exposición hasta implementar sus contratos y
autorización. IdP, roles productivos, SLO y RPO/RTO no se inventan.
