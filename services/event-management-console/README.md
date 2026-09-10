# Event Management Console

Consola React + TypeScript + Vite. La ruta `/administration` implementa la primera entrega del frontend de administración OS_06_01.DES. Ticketing consulta una copia aislada y persistente del mock de ServiceNow mediante HTTP.

## Administración

- Inventario, indicadores, búsqueda por nombre/ID y filtros de categoría y estado.
- Detalle accesible mediante diálogo nativo (Escape, foco y navegación por teclado).
- Consulta manual con tiempo máximo de 8 segundos y validación del contrato JSON.
- Estados de carga, error, inventario vacío y resultados vacíos.
- Demostración explícita, desactivada inicialmente y sin persistencia ni acciones reales.
- Enlaces locales a Kafka UI (8085), OpenSearch Dashboards (5601) e ITSM Dashboard (8088). Estos enlaces apuntan a la máquina del navegador y no prueban disponibilidad.

## Contrato de la API interna

`GET /api/administration/platform`, en el mismo origen, debe responder `application/json`:

```json
{
  "observedAt": "2026-09-10T18:00:00Z",
  "services": [{
    "id": "event-gateway",
    "name": "Event Gateway",
    "category": "Core",
    "description": "Recepción y validación de eventos.",
    "status": "healthy",
    "version": "1.0.0",
    "port": 8080
  }]
}
```

IDs únicos; estados `healthy`, `degraded`, `stopped`, `unknown`; versión y puerto admiten `null`. El backend deberá entregar exclusivamente metadatos públicos y aplicar autenticación y autorización. Las credenciales no pertenecen a este contrato.

El endpoint está conectado mediante Nginx a `frontend-management-api`, dentro de la misma red Compose. Consulta el runtime principal cada 30 segundos y al pulsar Actualizar. Estados adicionales: `error` y `completed`; diagnóstico, nombre de contenedor y cantidad de reinicios se muestran en el detalle. El catálogo conserva componentes detenidos o ausentes y distingue tareas completadas correctamente.

La consola existente está instalada en `http://localhost:8090/administration`. La fuente predeterminada es la API real, y no hay fallback automático a demostración.

## Idiomas

Selector Español / English en el encabezado. Traduce el shell de navegación y todo el módulo Administración / Inventario, incluidos filtros, estados, diagnósticos, detalle, errores y fechas. Guarda la preferencia en `console.language` y actualiza el atributo HTML `lang`. Español es el idioma inicial. La base React Context está en `src/shared/i18n`; el catálogo inglés es `en.json`, con español como texto fuente. Las pantallas de otros dominios mantienen su contenido previo y se incorporarán al catálogo al trabajar en esos módulos.

Inicio, detención y reinicio de servicios siguen pendientes de una API de comandos. Esta entrega consulta inventario y salud.

Ver [API y operación local](../frontend-management-api/README.md) y [validación bilingüe](validation-i18n-inventory.md).

## Desarrollo y validación

```bash
npm ci
npm run build
node --test tests/platform.test.mjs
npm run dev -- --host 127.0.0.1
```

Abrir `http://localhost:8090/administration` para probar el runtime instalado. El servidor Vite no incluye el proxy de producción; usar el despliegue Compose para pruebas de integración. Verificar ambos idiomas, filtros, detalle y cierre con Escape. Las pruebas de contrato cubren respuestas inválidas y errores HTTP.

El paquete de referencia `EventManagement_OS_06_01_DES_Frontend_Layer_PKC_v1.0.0.zip` se verificó con SHA-256 `3fcfea76a9222df0fb120a06b8b42b0dab5cfc6f11a94c25c5499487874dd27e`. Se aplicó exclusivamente su alcance de administración; los otros frontends mantienen su implementación previa. La consola y su BFF se despliegan en el Compose existente.

[Historial de cambios del componente](CHANGELOG.md).

## Tickets conectado al mock exclusivo

Ruta instalada: `http://localhost:8090/ticketing/tickets`.

Las tarjetas Todos, Abiertos, En progreso, Pendientes, Resueltos, Cerrados y Fallidos consultan por estado y conservan su selección. Buscar envía el número completo o parcial a la API; escribir por sí solo no dispara peticiones. Refresh repite la última consulta enviada. La prioridad se conserva como dato en la tabla, sin filtro desplegable.

Cerrar ticket requiere código y nota. El PATCH se envía a la API de la copia; el resultado se vuelve a consultar. Después de cerrar, se muestra el número cerrado sin filtro de estado para que la confirmación sea visible. Los datos residen en el volumen del mock, no en localStorage. La semilla conserva los 12 tickets originales; la prueba E2E dejó INC0019284 cerrado intencionadamente.

Conexión y control ahora muestra dos fuentes verificadas: Docker local para inventario (lectura) y ServiceNow de práctica para Tickets (consulta y cierre), con destino, endpoint, persistencia y botón Probar conexiones.

Ver [contrato del mock aislado](../../testing/mocks/servicenow-console/README.md).

```bash
node --test testing/services/event-management-console/snow-ticketing.test.mjs
python3 -m unittest discover -s testing/mocks/servicenow-console/tests -v
```

Ejecutar esas pruebas desde la raíz del repositorio. El módulo Tickets y las dos fuentes incluyen traducciones español/inglés. Los resúmenes de incidentes conservan el texto del proveedor.

## Encender, apagar y reiniciar

Detalle de servicio incluye las acciones permitidas por el administrador local, con confirmación, seguimiento asíncrono y actualización de salud al finalizar. Reutiliza `scripts/eventmanagement-services.sh` en runtime local; los datos se conservan al detener. Se bloquean operaciones simultáneas y el mismo ID nunca repite una ejecución. La API de control se administra por CLI; los componentes fuera del Compose y los jobs one-shot no ofrecen estas acciones.

ITSM Dashboard abre en `http://localhost:8091`. El inventario distingue esa superficie actual de ITSM Dashboard legacy (contenedor anterior, 8088). La superficie de 8091 comparte contenedor con Management Console.

### Criterios y Filtros / Cliente

Available below Automatizaciones at `/criteria-filters` and `/customers`. Records are persisted through `console-catalog-api` in PostgreSQL; visible columns and language are local preferences. See [catalog API deployment](../console-catalog-api/README.md) and [validation](validation-catalog.md). The existing 12 demo filters plus eight disabled examples show all five integration destinations. Saving configures the catalog; it does not publish routing rules or execute integrations.

## ESS administration

Administration → Estado de eventos · ESS (`/administration/ess`) reads the running Event State Service through the existing catalog API. Supports authorized tenants, event detail, explicit history and a separate operator-global quarantine summary. Spanish/English and both themes are supported. See [validation-ess.md](validation-ess.md) for access boundaries, evidence and rollback.

## Manual Blackouts v1

`/blackouts` now supports manual creation/versioning, validation, activation, deactivation, retirement, history and simulation through the existing Processor API. Select a tenant before writing. Legacy catalog rows remain read-only. See [validation-blackouts.md](validation-blackouts.md) for browser acceptance evidence and limitations.
