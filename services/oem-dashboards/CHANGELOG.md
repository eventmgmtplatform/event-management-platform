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

- Apariencia alineada con WebUI: densidad 14/20, cabecera de 64 px, sidebar de 240 px, marca EM, tablas y métricas compactas; temas Actual y Kyndryl con selector persistente e i18n. Adaptación visual en `src/appearance.css` basada en `services/event-management-console/src/shared/theme/appearance.css`.

## 2026-09-10 — IBM Carbon and LIVERPOOL themes

Added `carbon` and `liverpool` to the persistent theme selector in both localhost:8090 and localhost:8091, retaining Current and Kyndryl. Carbon uses Gray 10/white surfaces, Blue 60 #0f62fe and square controls. Liverpool uses #e10098 magenta from its public site CSS, with darker text/action accents for contrast. These are CSS appearance themes over existing React components, not an installation of @carbon/react or a replacement of the applications. Font stacks include brand fonts with local system fallbacks; no external font request is introduced.

References: https://carbondesignsystem.com/elements/color/overview/ and https://www.liverpool.com.mx/tienda/home (CSS pink-500 token #e10098).

Validation: both TypeScript/Vite builds passed; shared console container healthy after deployment. Browser verified both new themes on console Blackouts and operational Events dashboard; inspected navigation, tables, buttons and chart colors. Reload retained selections on both origins. English and Spanish selectors work. Preferences are stored separately for each port, as before.

- API Management: inventario por capacidades, filtro de servicio/estado y búsqueda; iconos SVG compactos para acciones con etiquetas accesibles y tooltips ES/EN, sin texto visible en botones. Verificación distingue endpoint de salud del servicio.

- Corregido `crypto.randomUUID is not a function` al usar un subdominio HTTP: UUID v4 con `getRandomValues` cuando randomUUID no está disponible. Regresión compartida con los controles y simulaciones de Console.
