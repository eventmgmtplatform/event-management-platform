# Administración REST y blackouts

La API local está habilitada y no utiliza autenticación ni roles. `X-Tenant-Id`
es obligatorio para particionar datos; `X-Actor-Id` es opcional y tiene valor
`local-operator`. Ambos son declaraciones del cliente, no identidad verificada.
La decisión de diferir seguridad está en [Defect Prevention](defect-prevention.md).

## Operaciones

- `GET /api/v1/rules`: `limit` 1..100, cursor `after` por ID.
- `GET /api/v1/rules/{id}`: versión opcional y ETag con revisión actual.
- `GET /api/v1/rules/{id}/history`: cursor de revisión y cambios inmutables.
- `POST /api/v1/rules/validate`: cuerpo `{rule: {...}}`.
- `POST /api/v1/rules`: cuerpo `{rule: {...}, reason: "..."}`.
- `POST /api/v1/rules/{id}/enable|disable|retire`: `{version: 1, reason: "..."}`.
- `POST /api/v1/simulations`: `{event: {...}, candidateRule?: {...}, evaluatedAt?: "..."}`.
- `GET /api/v1/explain/{processingId}`: evidencia persistida; no recalcula el pasado.

Las mutaciones requieren `Idempotency-Key` y `If-Match: "revision"` (cero para un
ID nuevo). La versión, recibo idempotente y auditoría SUCCESS se confirman en una
transacción. Reintentos idénticos devuelven el recibo original; cambios de contenido
o revisión obsoleta producen 409. Crear no activa. Habilitar exige una versión
con `enabled: true`. IDs y versiones son comunes a POLICY y blackout, y la
capacidad no puede cambiar entre versiones del mismo ID.

La simulación no escribe estado, configuración ni outbox. Usa el instante explícito
`evaluatedAt`, o `event.timestamps.receivedAt` si se omite. Una candidata se evalúa
aunque tenga `enabled: false`, sin habilitarla en el registro. `CANDIDATE` reemplaza
el snapshot por esa candidata; `ACTIVE` usa la configuración actualmente activa,
no reconstruye automáticamente la configuración histórica.

## Blackout implementado

El schema DA-07 se valida localmente (identificador del schema normalizado a URN).
Se admiten IMMEDIATE y SCHEDULED. Ambos requieren `validFrom` explícito para evitar
inicios implícitos variables. SCHEDULED requiere `validTo`; IMMEDIATE permite fin
abierto hasta desactivar. La ventana es `validFrom <= evaluatedAt < validTo`.
Se exige zona IANA; las ventanas se comparan como instantes, sin aritmética local
ambigua en cambios de horario. RECURRING se rechaza hasta cerrar DP-EP-02.

Scope usa igualdad exacta y conjunción. Vacío significa todo el tenant del registro.
Un selector ausente no coincide. `customerCode`, si aparece, debe coincidir con el
tenant del registro. Mapeo del envelope Gateway:

| Selector | Campo |
|---|---|
| customerCode | tenant canónico |
| node | resource.name |
| nodeAlias | resource.address |
| component | resource.component |
| instanceId | condition.instanceId |
| monitoringSolution | source.system |

No se recuperan campos arbitrarios de originalEvent ni se fabrican hechos de inventory.
Tags/attributes/recurrencia no implementados se rechazan, nunca se ignoran.

Ejemplo de definición (envolver en `rule` al crear):

```json
{"id":"maintenance-1","version":1,"type":"SCHEDULED","enabled":true,
 "scope":{"customerCode":"local-lab","node":"router-1"},
 "schedule":{"timezone":"America/Mexico_City",
 "validFrom":"2026-09-10T10:00:00Z","validTo":"2026-09-10T11:00:00Z"},
 "priority":10,"reason":"Maintenance","metadata":{"owner":"operations"}}
```

Un solo SELECT captura POLICY y blackouts activos por evento. La etapa Blackout
conserva todos los matches y elige motivo principal por prioridad descendente e
ID/version ascendentes. Propone SUPPRESS_INTEGRATIONS; no cambia el evento, no
crea lifecycle paralelo, no omite Correlation/PolicyEvaluation/ProcessingAudit y
no emite comandos. En ejecución usa un Clock y captura un instante por evento.

La migración 012 agrega recibos y auditoría administrativa. El despliegue conserva
backup e imagen anterior, aplica 011/012 y verifica disponibilidad. El alias
`/api/v1/enrichment` sigue funcionando.
