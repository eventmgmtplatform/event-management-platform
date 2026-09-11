# Changelog — event-management-console

Frontend React/TypeScript/Vite incorporado en septiembre, con navegación y base de ticketing.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Corte consolidado `06f046f`: quedaron integradas y publicadas las pantallas
  de administración y plataforma (Dashboard general, Event Management,
  Correlación, Routing, Policies, Inventory/Enrichment, Blackouts,
  AutoSuppression, AIOps Extensions, ESS, Cliente, Secrets/MockSecrets y
  Middleware), además de los plugins Ticketing, Notifications y CACF. Se
  conservaron los contratos same-origin, persistencia PostgreSQL, ETag/revisión,
  estados de error y las pruebas de navegador/Node asociadas.

- Detalle de servicio: encender/apagar/reiniciar mediante el CLI existente, confirmación, seguimiento y actualización de salud; auditoría persistente e idempotencia.
- Acceso y fila del dashboard ITSM actual en 8091, separado del contenedor legado de 8088.


- Tickets usa la API de una copia aislada de ServiceNow con SQLite y 12 incidentes iniciales. Buscar, refresh, filtros por tarjetas y cierre persistente.
- Conexión y control presenta dos fuentes verificables: Docker e incidentes del mock exclusivo.
- E2E en localhost: buscar INC0019284, cerrar, recargar y buscar confirma Cerrado; mock original conserva ID y tiempo de arranque.


- Inventario conectado al runtime principal mediante BFF interno y Nginx; actualización automática, diagnósticos y distinción de tareas completadas.
- Internacionalización del shell y Administración en español e inglés, con selector persistente.
- Instalación y pruebas de navegador en la consola existente de localhost:8090.

- Enlace local a OEM Dashboards (8091) desde herramientas especializadas de Administración; no sustituye el ITSM existente.

- Trabajo local de frontend de administración: navegación, snapshots de plataforma y sus validaciones. Pruebas Node centralizadas con enlace de compatibilidad; no equivale a una suite E2E de navegador.

## Historial confirmado en Git

### 2026-09-05 — `85af2ff1d7d3`

- Cambio registrado: feat(console): add event management console foundation.
- Alcance en este componente: `services/event-management-console/.dockerignore`, `services/event-management-console/Dockerfile`, `services/event-management-console/README.md`, `services/event-management-console/index.html` y 24 archivo(s) adicional(es).

## 2026-09-10 — Criteria, customers and tools

- Added Criterios y Filtros and Cliente below Automatizaciones, with PostgreSQL CRUD, search, configurable columns and Spanish/English forms.
- Reused existing delivery model, added customer configuration, audit and optimistic write conflict protection; 20 demo filters total.
- Restored Open WebUI and explicit legacy ITSM access alongside current ITSM on 8091; added specialized ITSM view shortcuts and observed container status.
- Added existing CLI start/stop/restart actions in service detail, tested on the isolated ServiceNow mock.

## 2026-09-10 — Themes and product dashboards

- Added persistent Actual/Kyndryl theme selector and productive typography/spacing across the console; Kyndryl is a Carbon-inspired CSS theme, not a Carbon React component migration.
- Added Middleware with Apache Kafka identification, live topology/topic health and access to the existing Kafka administration UI.
- Added separate Blackouts, Inventory Services, Policies, AIOps Extensions and AutoSuppression PostgreSQL read dashboards. Runtime rule publishing remains with the existing API.
- Added compact destination badges with optional group/Teams columns.

## 2026-09-10 — ESS administration

Added read-only ESS event list, exact lookup, detail/history and global quarantine via a server-side allowlisted proxy; es/en, both themes, request cancellation and explicit error/retry states. Deployed with unit, live HTTP and browser checks; see validation-ess.md.

## 2026-09-10 — Manual Blackouts v1

Connected the existing Blackouts screen to the versioned Processor write API via same-origin Nginx. Added explicit tenant selection, SCHEDULED/IMMEDIATE forms, separate save/activation, revision conflicts, persisted idempotent retry, terminal retirement, history and simulations. Browser acceptance and cleanup documented in validation-blackouts.md.

Look and feel: selector now includes Actual, Kyndryl, IBM Carbon and LIVERPOOL, persisted per browser origin. Both interfaces deployed and visually checked on 2026-09-10.

## 2026-09-10 — Administración del Processor

- Inventory/Enrichment: edición tipada, versiones independientes, simulación y catálogo anterior de consulta.
- AutoSuppression: registros locales, ventana finita, estado declarado, activación explícita e historial.
- Correlación: reglas ATTRIBUTE/GROUP, lectura paginada del registro mixto, simulación de ciclos y evidencia real por processingId.
- Reutilización del cliente same-origin, ETag, revisión optimista y reintentos idempotentes de Blackouts; traducciones ES/EN y temas existentes.

## Routing frontend — 2026-09-10

- Administración ROUTING base con ETag, versiones, historial y reintentos idempotentes.
- Simulaciones activas/candidatas, deduplicación y resultados inmutables; Lifecycle en solo lectura.
- Navegación y traducciones ES/EN; ver validation-routing.md.
