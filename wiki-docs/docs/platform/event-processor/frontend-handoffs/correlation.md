# Prompt para el chat de front — Correlación mínima

Integrar administración y simulación de reglas de Correlación en la consola existente.
Backend certificado por separado; este prompt requiere aceptación real desde navegador.
Conservar cambios concurrentes. Reutilizar componentes y proxy de Blackouts; no agregar
seguridad ni proveedores. No crear un editor de grupos persistidos: el motor los administra.

## Fuente y conectividad

Usar proxy same-origin `/api/processor/v1` hacia `event-processor:8082/api/v1`.
No hay API especializada `/correlations` ni catálogo dedicado de Correlación en este corte.
`GET /rules?limit=50&after=<cursor>` devuelve `{items,next}` con resúmenes mixtos
`id,latestVersion,activeVersion,status,revision`; no contiene tipo ni definición.
Para identificar Correlación, cargar `GET /rules/{id}` y seleccionar definiciones con
`rule.strategy`. Paginar con `next` hasta null; una página sin correlaciones no implica
que terminó el listado. Acotar concurrencia y mostrar progreso/errores de lectura.
No inferir capacidad a partir del nombre del ID. No inventar `type:CORRELATION`.

Elegir tenant explícito, enviar `X-Tenant-Id` en toda petición. `X-Actor-Id` es metadata
local sin autenticación. Los IDs comparten namespace con otros motores por tenant.
No usar localhost del navegador ni SQL directo.

## Administración versionada

| Acción | Endpoint relativo al proxy |
|---|---|
| Última definición | GET `/rules/{id}`; incluye rule, checksum, revisión y ETag |
| Versión activa/específica | GET `/rules/{id}?version=N` |
| Historial | GET `/rules/{id}/history?limit=50&after=0`, cursor de revisión en next |
| Validar | POST `/rules/validate` con `{rule}` |
| Crear versión | POST `/rules` con `{rule,reason}` |
| Activar/desactivar/retirar | POST `/rules/{id}/enable`, `/disable`, `/retire` con `{version,reason}` |
| Simular secuencia | POST `/simulations` con `{events,candidateRules?}` |
| Evidencia de evento real | GET `/explain/{processingId}` |

Cada mutación requiere `If-Match: "0"` para ID nuevo o ETag vigente y
`Idempotency-Key` único por acción lógica. Reintentar una respuesta incierta con
la misma clave, actor, cuerpo y revisión. Recibo de alta 201: `ruleId,version,revision,status,requestId`.
Manejar 409 sin sobreescribir, 422 semántica, 428 precondición, 404 y 503.

Guardar no activa: `enabled:true` permite habilitar posteriormente. Editar crea
`latestVersion+1`; la versión activa anterior continúa hasta habilitar la nueva.
Mostrar por separado última y activa; no presentar el GET por defecto como configuración
que necesariamente ejecuta el motor. Guardar/activar son dos operaciones; informar éxito parcial.
Desactivar/retirar usa activeVersion si existe o latestVersion en otro caso. Retiro
es terminal y conserva historia; no hay DELETE físico ni reutilización del ID.

## Formulario mínimo

```json
{
  "id":"by-node","version":1,"enabled":true,"priority":10,
  "strategy":"ATTRIBUTE",
  "scope":{"field":"resource.node","operator":"EXISTS"},
  "candidateSelection":{"windowSeconds":300,"maxCandidates":16,"activeOnly":true},
  "match":{"fields":["resource.node"]},
  "relationship":{"type":"GROUP"},"metadata":{"owner":"operations"}
}
```

Fijar ATTRIBUTE/GROUP y activeOnly=true. Ventana entera 1..86400 segundos;
maxCandidates 1..32; 1..4 campos String únicos de resource/enrichment. Usar lista
permitida: resource.node, resource.component, enrichment.resource.ciId,
enrichment.service.name, enrichment.assignment.group, enrichment.location.site,
enrichment.resource.class. Validar siempre en backend; no ofrecer eventId/eventKey,
campos numéricos, scripts, templates ni topología. Scope es AST tipado del motor,
no el objeto de coincidencias exactas de Blackouts. Para mínimo, reutilizar selector
field/operator/value o limitar formulario a resource.node EXISTS/EQ.
Máximo 8 reglas activas de correlación por tenant; mostrar rechazo de activación.

## Semántica que debe mostrar la interfaz

- Agrupa por tenant + ID lógico de regla + campos/valores. Versiones con claves
  equivalentes conservan el ciclo; cambiar campos cambia la identidad semántica.
- Recuperar un miembro no resuelve todo el grupo mientras otros sigan activos.
  Recuperar todos lo resuelve; una ocurrencia posterior abre otro ciclo/groupId.
- La ventana expira miembros al procesar eventos, sin barrido por reloj. Su borde
  inferior es inclusivo. maxCandidates limita historial retenido del ciclo, no sólo activos.
- Eventos tardíos/empatados y recuperaciones huérfanas tienen decisiones explícitas.
  CANDIDATE_LIMIT envía a DEAD_LETTER sin mutación parcial del grupo.
- Desactivar una regla detiene su evaluación, no borra ni resuelve sus grupos existentes.
- Correlacionar no equivale a crear ticket: Routing decide comandos por separado.

## Simulación y aceptación desde navegador

`events` admite 1..64 eventos Gateway normalizados con IDs únicos y receivedAt por evento.
Usar schemaVersion 1.1, tenant.code, resource.name, eventKey estable por miembro,
lifecycleAction OPEN/CLOSE, effectiveSeverity y timestamps.receivedAt.
`candidateRules` reemplaza el snapshot completo: no se suma a las reglas activas.
Omitirlo prueba configuración activa. No enviar event/evaluatedAt junto con events.
La respuesta tiene results y `correlationSource:EMPTY_REQUEST_SCOPED_STATE`:
la simulación comienza vacía, no consulta ni modifica grupos reales. Mostrar cada
`correlation.decisions[]`: ruleId, ruleVersion, reason, candidateCount, changed y group
nullable con groupId, cycle, revision, resolved, members. No esconder decisiones NO_MATCH.

Probar alta validada, recarga persistida, activación, edición v2 conservando v1 activa,
conflicto 409, cambio a v2, desactivación, retiro e historial. Probar secuencia dos OPEN
con claves distintas y mismo nodo, dos CLOSE, nuevo OPEN; comprobar agrupación,
resolución parcial/total y nuevo ciclo. Probar ventana/capacidad inválidas y estrategia
no soportada, selección de tenant y paginación. Guardar evidencia HTTP y capturas en
evidences; no declarar PASS de frontend sólo por ejecutar pruebas de backend.

DP-EP-01 conserva seguridad diferida; DP-EP-04/07/08 conservan estrategias adicionales,
API especializada y escalamiento. No ampliarlos en este incremento.
