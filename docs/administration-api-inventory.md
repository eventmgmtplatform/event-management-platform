# Inventario de APIs de administración

Inspección: 2026-09-10, checkout `feature/os-05-core-event-state-service`.
Describe código y lecturas HTTP del runtime compartido; no certifica todas las
operaciones de escritura. Administración de contenedores, configuración funcional
y salud son capacidades distintas.

| Componente | API implementada | Alcance y verificación |
|---|---|---|
| Event Gateway | `/api/v1/gateway/rules` | Reglas, historial, validación y simulación. `GatewayRulesApi.java`; GET sin clave devuelve 401 en runtime. |
| Event Processor | `/api/v1/rules`, `/api/v1/aiops`, `/api/v1/simulations`, `/api/v1/explain/{processingId}` | Administración funcional; `AdminResource.java` y `AiopsResource.java`. GET rules con tenant sintético devuelve 200. Identidad de cabeceras no equivale a autenticación/RBAC. |
| Integration Worker | `/integration/control` | GET/PUT de modo operativo; `IntegrationOperationalControlResource.java`. GET sin token devuelve 401. Diagnóstico GNM en `/internal/gnm/incidents`; API CACF funcional separada. No es CRUD universal de proveedores. |
| Event State Service | `/api/v1/state/events`, `/event`, `/history`, `/quarantine` | Lecturas por tenant con token vinculado; resumen de cuarentena con token de operador separado. API/CLI descritas en `event-state-service/admin-api.md`; rebuild y UI pendientes. |
| Frontend Management API | `/api/administration/platform`, `/sources`, `/operations` | Inventario y start/stop/restart asíncronos mediante CLI y lista permitida. GET platform vía consola devuelve 200. La propia API, jobs y componentes externos requieren CLI. |
| Console Catalog API | `/api/catalog/customers`, `/filters`, `/views/...` | CRUD de clientes/filtros y vistas de otros catálogos; GET customers vía consola devuelve 200. No sustituye administración del Processor. |
| OEM Dashboards API | `/api/dashboards/...` | Consulta de dashboards; configuración de fuentes por CLI. No API administrativa completa. |
| Consolas y dashboards web | Interfaces consumidoras | No son APIs administrativas independientes. |
| Kafka/PostgreSQL/OpenSearch | Herramientas/protocolos propios | CLI del producto y herramientas nativas; no contrato HTTP administrativo homogéneo de la plataforma. |

No existe todavía un contrato uniforme para autenticación, autorización,
versionado, errores y operaciones administrativas de todos los componentes.
Los 401 observados prueban que la ruta protegida responde; no prueban una
operación autenticada ni su efecto.

## Continuación ESS

El despliegue compartido de Processor, Worker y ESS ya incluye 016/017/020/021,
respaldo de ambos esquemas, imágenes fijadas y rollback. Tiene PASS de lifecycle
ESS con mocks; ver `event-state-service/validation.md`. CACF/GNM con mocks ya está activo y certificado en el runtime compartido;
ver `cacf/shared-activation.md`. Las APIs administrativas pendientes se mantienen.

La primera API ESS ya implementa consultas de estado/historial por tenant y
diagnóstico global de cuarentena sin payloads. Mutaciones, UI y autenticación
central siguen pendientes; ver `event-state-service/admin-api.md`.
