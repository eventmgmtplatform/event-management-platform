# OEM Dashboards

Alojada en el mismo Nginx/contenedor que Console, puerto 8091. No hay otro Nginx.

Superficie operativa React + TypeScript + Vite: Event Management, Ticketing, GNM y
CACF. Evolución aditiva del ITSM Dashboard; Console mantiene administración.

- [Arquitectura y decisiones](../../docs/dashboards/architecture.md)
- [05 Delivery: modelo PostgreSQL, ActionFiltering e i18n](../../docs/dashboards/delivery-model.md)
- [Contrato de consulta](../../docs/dashboards/contracts.md)
- [CLI, PostgreSQL, Compose y pruebas](../../docs/dashboards/operational-runbook.md)

`npm ci && npm run dev` inicia Vite en localhost:8091. El BFF Python está en
`../oem-dashboards-api`, puerto local 8092. Sin una fuente válida la UI muestra error;
no hay mock, fallback automático ni ingesta. `npm run build` genera el frontend.

Look and feel: selector now includes Actual, Kyndryl, IBM Carbon and LIVERPOOL, persisted per browser origin. Both interfaces deployed and visually checked on 2026-09-10.
