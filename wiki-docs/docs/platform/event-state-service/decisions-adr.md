# Decisiones del checkpoint ESS

- **ESS-BASE-001:** preservar contratos actuales del Worker (`resultId`, `tenant`,
  `SUCCESS`) mientras se descubre la brecha con el contrato V1. No consumir
  `events.normalized` como transición implícita.
- **ESS-BASE-002:** PostgreSQL sigue autoritativo. Bloquear por eventKey mediante
  `pg_advisory_xact_lock(hashtextextended(...))` antes del claim evita actualizaciones
  perdidas en la primera inserción. Colisiones de hash sólo serializan claves no
  relacionadas; no mezclan identidades. Todos los escritores ESS deben usar este
  repositorio. La protección no aplica a SQL externo que omita el protocolo.
- **ESS-BASE-003:** `@Transactional(rollbackOn=Exception.class)` mantiene claim y
  estado atómicos ante SQLException checked, validado con CDI/JTA real.
- **ESS-BASE-004:** el esquema global existente no se migra silenciosamente. Una
  colisión de tenant se rechaza y revierte el claim. Permitir claves iguales entre
  tenants queda pendiente de migración y estrategia de IDs OpenSearch.
- **ESS-BASE-005:** separar unidad, integración PostgreSQL aislada y certificación
  local de servicio. Compartir fixture y generar un reporte compacto. El flujo
  de proveedor se limita a ServiceNow mock; GNM es resultado sintético.
- **ESS-BASE-006:** mantener el estándar `feature/os-*`; no publicar ni promover
  un checkpoint sin completar y revisar la certificación. Evidencia y PKC fuera
  de Git conforme a `docs/git/governance.md`.

- **ESS-BASE-007:** resolver el bloqueo real de mensajes inválidos mediante tabla
  aditiva `ess_quarantine`, conservando payload, SHA-256, coordenadas Kafka y razón.
  Se confirma después del commit. No clasificar fallos SQL/HTTP como mensajes
  inválidos. Publicación de un topic de quarantine y retención siguen pendientes.
