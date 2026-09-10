# Changelog — OEM Dashboards

## Unreleased — 2026-09-10

- i18n español/inglés, selector persistente y formatos locales de fecha/número.
- Módulo 05 Delivery con distribución interactiva por destino/APPLID/severidad,
  estados, criterios, detalle de acciones y filtros en URL; consulta PostgreSQL real.
- Migración 022 y 12 filtros demo con procedencia explícita. Ver modelo Delivery.

- Foundation 0.1.0: Event Management, Ticketing, GNM y CACF como superficie de operación.
- React/TypeScript/Vite, tokens de WebUI, consulta real vía BFF; sin datos mock.
- Filtros, distribución, paginación, estados de carga/error/vacío y checklist operativo.
- Overlay Compose independiente en loopback 8091, conservando ITSM 8088 y Console 8090.
- [Arquitectura](../../docs/dashboards/architecture.md) y [validación](../../docs/dashboards/validation.md).

- Desplegado en Nginx compartido de Console (:8090 y :8091), sin segundo Nginx. PostgreSQL default; datos DEMO-DASHBOARDS aislados (27 registros).

- 06 Data Collection: originales durables PostgreSQL, día/histórico, filtros y descarga por UUID con SHA-256.
- 07 API Management: disponibilidad HTTP por servicio y testing/stop/start/reboot mediante el controlador existente.
