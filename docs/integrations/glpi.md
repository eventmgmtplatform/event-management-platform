# GLPI · línea base

GLPI usa el contrato canónico `integration.commands` / `integration.results`, el router de proveedores y el ledger durable del Integration Worker. Los eventos siguen entrando por la API pública existente; una regla ROUTING con `target: GLPI`, `type: CREATE_TICKET`, `configuration: default` y `correlationRuleId` produce el comando. El editor Routing permite seleccionar ServiceNow o GLPI. No hay una API pública adicional para saltarse el procesador.

## Componentes

| Componente | Responsabilidad GLPI |
| --- | --- |
| Event Processor | Routing GLPI y generación del comando con identidad propia por proveedor |
| Integration Worker | Validación nativa, sesión, creación, solución, seguimiento, checkpoint y reconciliación |
| Event State Service | Validación GLPI, deduplicación del resultado, proyección separada en PostgreSQL y estado GLPI en OpenSearch |
| PostgreSQL | `event_management.glpi_integration_state`, PK `(tenant,event_key)`, y `dashboard_read.glpi`; no escribe `ticket_number` ni `servicenow_status` |
| GLPI Ticketing API | Sesiones/credenciales del lado servidor, consulta paginada y cierre confirmado |
| WebUI | ServiceNow - Ticketing y GLPI - Ticketing en el mismo plugin; proveedor e IDs diferenciados; cierres dirigidos al adaptador correspondiente |
| OEM Dashboards | Dashboard GLPI y destino GLPI en catálogo Delivery |
| Administración | Inventario, API Management, conexiones y control de los nuevos servicios |
| GLPI mock | API nativa de prueba, SQLite/volumen propio sin datos ServiceNow |

El estado consolidado conserva un resumen por proveedor en `integration_state`; los datos y referencias GLPI tienen su tabla independiente. El plugin unifica la presentación, no los almacenes. Un fallo de un proveedor conserva las filas disponibles del otro y muestra que el inventario es parcial.

## API nativa

Implementación basada en la [documentación oficial REST V1 de GLPI](https://help.glpi-project.org/documentation/modules/configuration/general/api/api), disponible en `/apirest.php`. No implementa la API V2/OAuth.

- `GET /initSession`: `Authorization: user_token …`, `App-Token`.
- Peticiones posteriores: `Session-Token`, `App-Token`; `GET /killSession` al terminar.
- `POST /Ticket`: `input.name`, `content`, `type`, `urgency`, `impact`, `priority`, `entities_id`, `itilcategories_id`, `_users_id_requester`, `_users_id_assign`, `_groups_id_assign`.
- `RESOLVE_TICKET`: `payload.ticketId` numérico y `content` o `closeNotes`; crea `ITILSolution` y confirma status 5/6 mediante GET.
- `CLOSE_TICKET`: `payload.ticketId` numérico; sólo permite cerrar tickets en estado 5, ejecuta `PUT /Ticket/{id}` con `status: 6` y confirma `CLOSED_CONFIRMED` mediante GET. Si ya está en 6, la operación es idempotente y sólo consulta.
- `APPLY_AUTOMATION_RESULT`: `payload.ticketId` y `content`; crea `ITILFollowup` privado.
- Los resultados CACF/NEXT pueden emitir `APPLY_AUTOMATION_RESULT` con `request.ticket.provider: "GLPI"` y `request.ticket.id`/`sysId`; el comando usa el ID numérico nativo y conserva el mismo contrato de evidencia.
- Las solicitudes de automatización aceptan ese mismo bloque `ticket` con `provider: "GLPI"`; el ID debe ser entero positivo y los grupos de asignación siguen siendo obligatorios para el contrato CACF.
- WebUI: `GET /api/glpi/tickets`, `POST /api/glpi/tickets/{id}/close` con `{ "solutionTypeId": 0, "note": "…" }`. El servidor crea solución si hace falta y solicita status 6, comprobando el resultado.

GLPI: estados 1 nuevo, 2 asignado, 3 planificado, 4 pendiente, 5 solucionado, 6 cerrado. La UI combina 2/3 como En progreso. Urgencia/impacto 1–5; prioridad 1–6; tipo 1 incidente / 2 solicitud. La severidad OEM 1–5 se conserva como urgencia/impacto por defecto; severity 0 usa 1. Las entidades y categorías se muestran como IDs, no como nombres de cliente/torre inventados.

Ejemplo canónico: `config/glpi/create-ticket.example.json`. En producción se deben usar IDs de entidad, categoría, actores y tipos de solución autorizados para el usuario GLPI.

## Recuperación

El ledger reclama `commandId` antes de escribir. Un checkpoint guarda ID del ticket y de la mutación tras aceptación. REPLAY devuelve el resultado persistido; IN_PROGRESS no escribe. La recuperación del checkpoint sólo hace GET. Si CREATE quedó incierto antes del checkpoint, se busca un marcador exacto de comando en el nombre del ticket; sólo una coincidencia puede confirmar la identidad. Ausencia o múltiples coincidencias requieren revisión, nunca otro POST automático. Soluciones/seguimientos sin checkpoint también requieren revisión. El scheduler existente de recuperación reingresa comandos vencidos al mismo flujo.

Las respuestas 400/401/403/404/405/422 producen fallo explícito; timeouts o fallos ambiguos después de escribir dejan el claim pendiente. La recuperación conservadora prioriza evitar tickets/notas duplicados. Un operador debe investigar los pendientes sin coincidencia; no debe reenviarlos con otro commandId para forzar la creación.

## Configuración y despliegue

Variables backend: `GLPI_BASE_URL` (incluye `/apirest.php`), `GLPI_APP_TOKEN`, `GLPI_USER_TOKEN`, `GLPI_TIMEOUT_MS` (worker, 5000 por defecto), `GLPI_TIMEZONE` (API de consulta, UTC por defecto para fechas GLPI sin offset). Las credenciales sólo se configuran en backend. Compose apunta por defecto al mock con tokens locales de prueba. Para una instancia real, habilitar REST V1 y conceder permisos sobre tickets/soluciones/seguimientos y sus entidades.

1. Aplicar las migraciones existentes y luego `infrastructure/postgres/init/027-glpi-integration.sql` con `psql -v ON_ERROR_STOP=1`. Incluye vista de lectura y ampliación de destinos Delivery. En volúmenes existentes Docker no reejecuta automáticamente los scripts init.
2. Construir/desplegar los servicios modificados: event-processor, integration-worker, event-state-service, glpi-mock, glpi-ticketing-api, frontend-management-api, console-catalog-api, event-management-console, oem-dashboards-api y oem-dashboards según la composición usada en el entorno.
3. Configurar/activar una regla de correlación y una ROUTING dirigida a GLPI; activar el consumidor según el control operativo existente.
4. Verificar `/ready` del GLPI Ticketing API, crear un evento por el gateway y comprobar comando, resultado, proyección GLPI y ambas interfaces.

El mock arranca vacío: los tickets aparecen al crear comandos; no se presentan tickets ficticios como datos del proveedor real. Las pruebas usan una base temporal y no tocan el volumen del runtime.

## Alcance de esta base

Incluye creación por routing y comandos de solución, cierre y seguimiento GLPI. El perfil avanzado ServiceNow→GNM→CACF de `PostgresLifecycleSession` sigue siendo específico de ServiceNow: GLPI rechaza ese perfil explícitamente, porque los grupos, work notes y callbacks CACF actuales usan el contrato ServiceNow. Los catálogos Delivery son configuración; registrarlos no habilita por sí solo ese workflow. Extender esa orquestación requiere un perfil GLPI propio y un callback CACF consciente del proveedor.

La API de consulta enumera hasta 10000 tickets de la cuenta GLPI configurada, con páginas de 100 y error explícito al exceder el límite. El alcance de entidad/tenant de lectura depende de los permisos de esa cuenta; no se trata de un portal multiusuario con autorización nueva.

## Validación

`python3 testing/run.py glpi` ejecuta pruebas de contrato HTTP local, worker y adaptador frontend (requiere Java 21/Maven, Node y TypeScript instalados). Las pruebas de HTTP local requieren permiso para sockets. Para regresión: suites Maven de processor/worker/ESS, builds TypeScript/Vite y pruebas de dashboards y administración. No equivalen a certificación contra una instancia real ni a despliegue del runtime.
