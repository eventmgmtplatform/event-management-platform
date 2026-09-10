# Prompt para el chat de front — Inventory/Enrichment mínimo

Conecta la administración de inventario del Processor y sus planes de enrichment
a la consola existente. El backend se revisa/certifica primero; el objetivo de este
prompt es implementar y probar la escritura desde navegador. No modificar contratos
de eventos, inventar sincronización de catálogos ni agregar seguridad/proveedores.
Conservar los cambios de otros trabajos en el workspace.

## Distinguir las fuentes

- `/api/catalog/views/inventory-services`: catálogo anterior `event_management.inventory_resource`.
  Su existencia no implica que el Processor consuma esos registros. Mantenerlo como
  consulta claramente identificada; no editar sus IDs mediante la API de reglas.
- `/api/catalog/views/inventory-records`: registros INVENTORY del Processor.
- `/api/catalog/views/enrichment-plans`: planes ENRICHMENT del Processor.

Las dos lecturas nuevas usan `event_processor.rule_definition` + `rule_version`, la
misma fuente que ejecuta el motor. Responden `{items,truncated,mode:"read_only"}`;
cada fila incluye `id,tenant,name,enabled,source,status,active_version,latest_version,
revision,configuration`. El límite es 2000 filas, no paginación ilimitada.
La configuración mostrada prefiere la versión activa, si existe.

Presentar inventario ejecutable y planes como dos secciones vinculadas. Un registro
INVENTORY sin plan ENRICHMENT aplicable **no enriquece eventos**. No usar la palabra
«activo» para confundir registro guardado, versión habilitable y configuración activa.

## Conectividad y API de escritura

Processor: `http://event-processor:8082/api/v1` dentro de Docker. Reutilizar el proxy
same-origin `/api/processor/v1/` y cliente del módulo Blackouts existente; verificar
que preserve query, ETag y códigos. No usar localhost del navegador ni escribir SQL.

Todas las peticiones llevan `X-Tenant-Id` del tenant elegido. `X-Actor-Id` es metadata
del operador local, no identidad autenticada. Vista «Todos»: exigir seleccionar tenant
antes de escribir. IDs comparten namespace con los demás tipos de reglas.

| Acción | API del Processor |
|---|---|
| Leer última versión | GET `/rules/{id}`; respuesta con ETag, revision, latestVersion, activeVersion, status, rule y checksum |
| Leer versión específica | GET `/rules/{id}?version=N` |
| Historial | GET `/rules/{id}/history?limit=50&after=0`; cursor de revisión |
| Validar definición | POST `/rules/validate` con `{rule}` |
| Crear o guardar nueva versión | POST `/rules` con `{rule,reason}`; 201 y recibo `{ruleId,version,revision,status,requestId}` |
| Activar/desactivar/retirar | POST `/rules/{id}/enable`, `/disable`, `/retire` con `{version,reason}` |
| Simular | POST `/simulations` con `{event,candidateRules?,evaluatedAt?}` |

Mutaciones requieren `If-Match: "0"` para ID nuevo o ETag actual para cambios, más
`Idempotency-Key` único por acción lógica. Reintento incierto conserva exactamente
clave, actor, cuerpo y revisión. Manejar 409 refrescando y reconciliando; no sobrescribir.
Mostrar 400/413/422 validación, 404 ausente, 428 precondición, 503 indisponibilidad.

Guardar nunca activa: enviar `enabled:true` permite habilitar la versión después.
Editar crea `version=latestVersion+1`; la versión previamente activa sigue aplicando
hasta `/enable` de la nueva. Guardar/activar son dos operaciones: informar éxito parcial
si sólo se guardó. No simular atomicidad entre guardar un registro y guardar un plan.
Desactivar/retirar usa activeVersion si existe, si no latestVersion. Retiro es terminal,
conserva historial y no permite reutilizar ID. No hay PUT ni DELETE físico de reglas.

## Formulario de registro INVENTORY

```json
{
  "id":"ci-router-1", "version":1, "type":"INVENTORY", "enabled":true,
  "priority":10, "scope":{"customerCode":"tenant-demo","node":"router-1"},
  "facts":{"resource.ciId":"ci-001","assignment.group":"network",
           "resource.managed":true,"service.criticality":3},
  "metadata":{"owner":"operations"}
}
```

Scope: igualdad exacta y conjunción. Selectores `customerCode`, `node`, `nodeAlias`,
`component`, `instanceId`, `monitoringSolution`. Exigir al menos uno distinto de
customerCode; no permitir inventario global vacío. CustomerCode explícito debe
coincidir con el tenant. Omitir campos opcionales vacíos.

Hechos permitidos (uno o más, máximo siete):

| Hecho | Tipo |
|---|---|
| resource.ciId, service.name, assignment.group, location.site, resource.class | String no vacío, máximo 4096 caracteres |
| resource.managed | Boolean JSON; no string "true" |
| service.criticality | Número JSON; no string |

No añadir hechos libres ni valores null. No imponer un rango de criticality que el
contrato no define. Prioridad decide precedencia entre registros coincidentes: mayor
primero, después ID y versión ascendentes. El primer valor por campo gana; conflictos
y procedencia quedan disponibles en el resultado, sin sobrescritura silenciosa.

## Formulario de plan ENRICHMENT

```json
{
  "id":"inventory-lookup", "version":1, "type":"ENRICHMENT", "enabled":true,
  "priority":10,
  "condition":{"field":"resource.node","operator":"EXISTS"},
  "actions":[{"type":"LOOKUP_INVENTORY","parameters":{"required":true}}],
  "metadata":{"owner":"operations"}
}
```

Una sola acción LOOKUP_INVENTORY. La condición usa la DSL tipada existente; no puede
depender de `enrichment.*`. Para el formulario mínimo ofrecer condiciones simples
válidas de recurso; no incorporar un editor arbitrario de reglas ni RUN_SCRIPT.
Required=true sin registro coincidente causa FAILED/DEAD_LETTER. Required=false
sin registro produce NOT_FOUND y continúa. Varios planes aplicables comparten lookup.
Deshabilitar/retirar registros puede dejar planes requeridos sin datos: advertir esa
consecuencia funcional y permitir simular antes, sin bloquear decisiones del operador.

## Simulación y presentación del resultado

Usar un evento Gateway 1.1 con `schemaVersion:"1.1"`, eventId único, eventKey,
`tenant:{code}`, `resource:{name}`, summary, `lifecycleAction:"OPEN"` (o CLOSE para
recuperación), effectiveSeverity y `timestamps:{receivedAt:<ISO 8601>}`.
Sin candidateRules se consulta el snapshot activo; con candidateRules se sustituye
por todo el conjunto candidato. Para probar un borrador de plan, incluir también sus
registros INVENTORY y policy si se desea probar consumo, no sólo el plan.

Mostrar enrichment.status, facts, lookups, provenance, conflicts y degraded; además
directive y etapa ContextEnrichment. Los estados de resultado son SUCCESS, NOT_FOUND,
PARTIAL, FAILED. FOUND es estado del lookup, no del resultado global. Sin plan aplicable
no se afirma que exista un CI. La simulación no escribe estado de eventos ni outbox.

## Aceptación obligatoria desde navegador

1. Crear registro y plan; guardarlos no enriquece antes de activar.
2. Activar ambos; simular el recurso correcto y mostrar hechos/procedencia reales.
3. Otro recurso y tenant no reciben hechos del registro.
4. Guardar v2 conservando v1 activa; activar v2 cambia hechos; historial intacto.
5. Registros coincidentes con distinto valor: mostrar selección y conflicto.
6. Inventario ausente: demostrar diferencia required/opcional sin inventar defaults.
7. Dos pestañas/reintentos: no sobrescribir ni duplicar versión.
8. Desactivar/retirar; refrescar; conservar resultado y estado autoritativo del backend.
9. El catálogo anterior sigue separado y sin falsa activación en el Processor.
10. Fallo HTTP visible, sin fallback a mock ni mensaje de éxito.

Conservar evidencias en `evidences/inventory-enrichment/frontend-<fecha>/`. No declarar
PASS de escritura frontend usando únicamente la certificación backend. Referencias:
`docs/event-processor/enrichment-and-inventory.md`, `inventory-record-v1.schema.json`
y `testing/certifications/processor-inventory-enrichment-certification.py`.
