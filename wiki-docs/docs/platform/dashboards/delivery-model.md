# Delivery — modelo PostgreSQL y dashboard 05

Fecha: 2026-09-10. Contrato de consulta `1.0`. Migración aditiva:
`infrastructure/postgres/init/022-delivery-filter-catalog.sql`.

## Propósito y autoridad

Delivery consulta **configuraciones de filtros**, no tickets, notificaciones,
eventos entregados ni ejecuciones CACF. El catálogo se almacena en PostgreSQL y
se consulta mediante el mismo BFF y selector de fuentes de OEM Dashboards.
Frontend `/dashboards/delivery`, API `/api/dashboards/delivery`, menú `05 · Delivery`.
Se mantiene un único Nginx/contenedor `event-management-console`: Console en 8090,
Dashboards en 8091. El BFF es interno.

El modelo es **nuevo**. `event_state` y `automation_execution` no contienen
configuraciones ActionFiltering; `integration_configuration` describe conexiones
a proveedores, no criterios de coincidencia. No se mezclan esos conceptos ni se
presenta el nuevo catálogo como la configuración activa del Processor.
No hay consumidores del catálogo en workers ni generación de comandos.


## Diccionario de datos

### event_management.delivery_filter

| Columna | Tipo / regla | Significado |
|---|---|---|
| filter_id | text PK, 1–128 caracteres | Identidad estable del registro |
| name | text requerido, 1–200 | Nombre del filtro |
| description | text, predeterminado vacío | Descripción pública de la configuración |
| customer_code | text requerido, 1–100 | Cliente; C00 representa global legacy |
| applid | text nullable, 1–128 | APPLID específico; null significa cualquiera |
| filter_state | smallint 0/1/2 | Habilitado / deshabilitado / auditoría |
| filter_weight | integer, predeterminado 0 | Peso de referencia; no una decisión de ejecución |
| severities | smallint[] nullable | Conjunto de severidades 0–5; null significa cualquiera; array vacío y null interno se rechazan |
| criteria | jsonb objeto | Predicados declarativos por campo |
| origin | manual / legacy / demo | Procedencia explícita |
| legacy_filter_id | text nullable | Identidad original, cuando exista importación |
| updated_at | timestamptz | Última modificación; escritor debe actualizarla |

### event_management.delivery_filter_target

| Columna | Tipo / regla | Significado |
|---|---|---|
| filter_id | FK delivery_filter | Filtro al que pertenece |
| target | gnm / snow / cacf / chatops / extensions | Destino de configuración |
| behavior | enable / force_off / overlay | Configurar acción, forzar apagado o superponer |
| action_reference | text nullable | Referencia pública a una acción, sin secretos |
| assignment_group | text nullable | Grupo destino |
| delay_seconds | integer ≥0 nullable | Demora normalizada; no códigos negativos legacy |
| depends_on_ticketing | boolean | Dependencia declarada de Ticketing |

PK `(filter_id,target)`: una entrada normalizada por destino y filtro. Eliminar un
filtro elimina sus asociaciones por FK, no eventos ni ejecuciones. Puede existir
un filtro sin acciones; cuenta en el total y se muestra explícitamente.

**Límite de normalización:** la V1 no representa dos acciones Hunt/Broadcast distintas
del mismo destino dentro de un filtro. Una importación que encuentre ese caso debe
reportarlo para ampliar el modelo, no colapsarlo silenciosamente. Tampoco transforma
automáticamente códigos de demora negativos ni dependencias especiales del legacy.

### Criterios admitidos

Formato: `{"Component":{"operator":"eq_ci","value":"database"}}`.
`eq` conserva comparación tipada de número/boolean/string; `eq_ci` y `regex_ci`
exigen texto. Los predicados se muestran como **datos**, no se evalúan como SQL,
shell, JavaScript o expresiones del navegador. No hay editor/ejecutor de scripts.

Campos permitidos: IBMManaged, ResourceId, Service, SubAccount, Subsystem,
Application, InstanceId, SubComponent, Component, ComponentType, ResourceUsage,
OSType, MsgId, AlertKey, AlertGroup, ResourceType, EventType, MonitoringSolution,
Location, SourceType y OutsideServiceHours. APPLID, cliente y severidades tienen
columnas específicas y no deben duplicarse en criteria. Ausencia de un campo
significa sin condición adicional, no un valor vacío.

La función SQL `delivery_criteria_valid` rechaza campos/operadores desconocidos,
valores compuestos o null, predicados incompletos, texto >512 caracteres y objetos
>16 KB. No admite claves de credenciales. Descripción, referencias y grupos deben
contener exclusivamente configuración pública; secretos permanecen fuera del catálogo.

### Vista de lectura

`dashboard_read.delivery` devuelve una fila por filtro y agrega las asociaciones
como `targets` JSON. Se otorga SELECT a `oem_dashboard_reader`; el LOGIN del BFF
hereda el rol. No se otorgan escrituras ni acceso directo a tablas canónicas.
La CLI admite `source set postgresql --dashboard delivery` o `api` con el mismo
contrato. Health y smoke ahora incluyen los cinco módulos.

## Mapeo de destinos

| Destino | Referencia legacy | Tratamiento |
|---|---|---|
| snow | TicketAction*, TicketGroup, TicketForceOff | Configuración de Ticketing; no asumir que todo ticket legacy usa ServiceNow |
| gnm | HNAction*, BNAction*, HNForceOff, BNOverlay | Notificaciones; mapeo de acciones múltiples requiere resolver el límite mencionado |
| cacf | GenericAction / acciones de autocorrección | Clasificar por semántica de la acción; no convertir cualquier GAAction en CACF |
| chatops | ChatOpsAction*, ChatOpsGroup, ChatOpsForceOff | Destino ChatOps de catálogo |
| extensions | Forward / GenericAction no clasificada | Extensiones explícitas; sin ejecución arbitraria |

El mapeo guía la carga futura. La existencia de una asociación no prueba que el
proveedor esté conectado ni que una acción haya sido enviada.

## API y semántica de distribución

`GET /api/dashboards/delivery` admite:

| Query | Regla |
|---|---|
| target | Uno de los cinco destinos; vacío = todos |
| applid | Exacto case-insensitive; `__ANY__` selecciona registros con null (valor reservado al protocolo) |
| customer | Cliente exacto, sin incluir C00 implícitamente |
| state | 0, 1 o 2; vacío = todos los registrados |
| severity | 0–5 incluye filtros con esa severidad y comodines; `any` sólo comodines |
| q | Subcadena literal de ID/nombre/descripción; no SQL ni comodines |
| page / limit | 1–100000 / 1–100, predeterminados 1/25 |

Los criterios se combinan mediante AND. Seleccionar un destino o barra reinicia
la página y actualiza distribuciones y tabla. Repetir clic deselecciona; los chips
permiten retirar APPLID/severidad/destino por separado. Estado/cliente/búsqueda y
selecciones quedan en la URL, incluyendo historial Atrás/Adelante.

Respuesta: schemaVersion=1.0, domain=delivery, source, observedAt, lastUpdatedAt,
total, page, limit, facets={targets,applids,severities,states}, rows.
Cada bucket es `{value,count}`; cada row expone identidad, cliente/APPLID, estado,
peso, severidades, criterios, origen y asociaciones.

`total` cuenta filtros distintos **de todo el resultado filtrado**, no de la página.
APPLID y estado particionan el total; destinos y severidades pueden sumar más porque
un mismo filtro aparece en varias categorías. APPLID agrupa sin distinguir mayúsculas.
Los comodines tienen bucket separado; al seleccionar severidad 5, esos filtros
siguen siendo aplicables. Tras seleccionar GNM, las otras tarjetas cuentan destinos
co-configurados de los mismos filtros; no son totales globales. Todos los agregados
y filas se leen en una transacción REPEATABLE READ / READ ONLY.

Filas ordenadas por peso descendente e ID ascendente. El diálogo de detalle expone
referencia, grupo, demora y dependencia de Ticketing. Error, vacío y carga son
estados separados; nunca se sustituye un fallo de PostgreSQL por datos mock.

## Carga y demostración

Requisitos: schema event_management, dashboard_read y rol reader de migración 018.
Aplicar 022 con el administrador PostgreSQL. Para registrar datos, un proceso
administrativo autorizado escribe filtro y asociaciones en una transacción; la
UI sólo consulta. No se añade un pipeline de ingesta ni se importan datos legacy
automáticamente en esta entrega.

`scripts/dashboards/seed-delivery-demo.sql` es opt-in e idempotente. Carga **12 filtros**
con `origin=demo`, cliente `DEMO-DASHBOARDS` y IDs `demo-delivery:*`:
5 asociaciones GNM, 5 SNOW, 3 CACF, 3 ChatOps y 3 Extensions. Hay APPLID CORE,
PAYMENTS, ERP, NETWORK, WEB y un comodín; estados 0/1/2, criterios y acciones variadas.
Los datos de catálogo no se consumen por los workers. No son exportación de
configuraciones productivas. La UI marca la procedencia demo.

Rollback de demo: eliminar exclusivamente filtros `origin='demo'` cuyo ID empiece
por `demo-delivery:`; cascada retira sólo sus asociaciones. No se elimina el modelo.
Rollback de frontend: restaurar la imagen anterior compartida de Console; preserva
tablas/configuración para el siguiente despliegue. Cambiar fuente usa la CLI con
validación y rollback existente.

## i18n

Catálogo de mensajes `src/i18n.tsx`, idiomas es/en. Español inicial; selector de
cabecera persistido en localStorage `oem.language`. Storage bloqueado no impide uso.
Cambiar idioma mantiene filtros y datos; cambia textos, errores conocidos, unidades,
fechas, números, título y atributo HTML lang. IDs, estados técnicos, APPLID y criterios
del cliente no se traducen ni mutan. La preferencia es por origen 8091, no compartida
automáticamente con Console 8090.

## Validación

Suite PostgreSQL aislada: reejecución de migración/seed, conteos DISTINCT,
APPLID case-insensitive/comodín, severidades/comodín, auditoría, paginación,
inyección como texto, restricciones y rol de lectura. Paridad API/PostgreSQL para
los cinco dominios. Frontend: contrato, totales inconsistentes, traducciones de los
cuatro dominios y render en inglés con preferencia guardada. TypeScript y Vite build.
No certifica paridad completa de ejecución legacy ni importación de datos reales.
