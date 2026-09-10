# Prompt para frontend — Policy Engine

Integrar administración, validación y simulación de POLICY en la consola existente.
Reutilizar proxy/cliente de Blackouts y editor tipado de condiciones disponible. No
modificar otros motores ni introducir seguridad. Certificar escritura real desde
navegador y conservar evidencia HTTP/capturas en evidences.

## Lectura y autoridad

`GET /api/catalog/views/policies` devuelve items,truncated,mode:read_only. Mezcla
fuentes: Event Processor usa el registro que ejecuta el motor; Catálogo PostgreSQL
usa event_management.event_policy y no es equivalente. No enviar IDs heredados al
CRUD del Processor. Filas Event Processor contienen id,tenant,status,enabled,
active_version,latest_version,revision,configuration. configuration prefiere activa.
Límite 2000 filas: no asumir listado completo si truncated=true.

Administración mediante proxy same-origin `/api/processor/v1`, destino interno
`event-processor:8082/api/v1`. Cada petición lleva X-Tenant-Id explícito; X-Actor-Id
es metadata local no autenticada. No usar localhost del navegador ni SQL directo.

| Acción | Ruta relativa al proxy |
|---|---|
| Listar registro por tenant | GET `/rules?limit=50&after=<cursor>`; items,next, resúmenes mixtos sin tipo |
| Leer última definición | GET `/rules/{id}`; rule,checksum,revision,status,latestVersion,activeVersion y ETag |
| Leer versión específica | GET `/rules/{id}?version=N` |
| Historial | GET `/rules/{id}/history?limit=50&after=0`; next es cursor de revisión |
| Validar | POST `/rules/validate` con `{rule}` |
| Guardar versión | POST `/rules` con `{rule,reason}` |
| Activar/desactivar/retirar | POST `/rules/{id}/enable`, `/disable`, `/retire` con `{version,reason}` |
| Simular | POST `/simulations` con `{event,candidateRules?}` |
| Auditar evento real | GET `/explain/{processingId}` |

Si se usa listado de reglas, paginar hasta next=null, cargar detalles con concurrencia
acotada y seleccionar rule.type=POLICY. No inferir tipo por ID. Namespace compartido
por tenant. Mutaciones: If-Match con ETag vigente o `"0"` para nuevo ID, más
Idempotency-Key por acción lógica. Reintento incierto conserva clave/cuerpo/actor/revisión.
Alta devuelve 201 y recibo ruleId,version,revision,status,requestId.
Manejar 409 refrescando sin sobrescribir; 422,428,404,503 explícitos.

Guardar latestVersion+1 no activa: enabled=true permite habilitar después. Mostrar
última y activa por separado; informar éxito parcial si falla activación tras guardar.
Desactivar/retirar usa activeVersion si existe o latestVersion. Retiro terminal, sin
DELETE físico ni reutilización del ID. No borrar metadata soportada al editar.

## Formulario y evaluación

```json
{
  "id":"suppress-low-severity","version":1,"type":"POLICY","enabled":true,
  "priority":10,"condition":{"field":"event.severity","operator":"LTE","value":1},
  "actions":[{"type":"SUPPRESS_INTEGRATIONS"}],"metadata":{"owner":"operations"}
}
```

Acciones permitidas: CONTINUE, STATE_ONLY, SUPPRESS_INTEGRATIONS, CORRELATE_ONLY.
Sin target, scripts, payload de proveedor ni credenciales. No ofrecer GENERATE_COMMANDS
ni DEAD_LETTER como acciones; Routing es otro motor. Metadata admite owner,description,tags.

Condition usa hojas field/operator/value y grupos all/any/not. Validar siempre en
backend. Respetar tipos del [contrato](../rules-and-contracts.md): números sin coerción,
IN/NOT_IN colección homogénea, BETWEEN dos límites ordenados inclusivos, EXISTS/NOT_EXISTS
sin value. REGEX coincide con toda la cadena y tiene gramática restringida; no ofrecer
regex arbitraria como si toda expresión fuera válida. Campos resource/enrichment
son los del contrato tipado; no inventar rutas de estado. Valores ausentes no coinciden
con predicados ordinarios; not niega su hijo. Límites: 16 acciones y 256 reglas activas/tenant.

Todas las coincidencias aportan propuestas. Prioridad descendente, luego ID/versión,
ordena evaluación; una prioridad alta no anula directivas más restrictivas.
Precedencia global: DEAD_LETTER > STATE_ONLY > SUPPRESS_INTEGRATIONS > CORRELATE_ONLY
> GENERATE_COMMANDS > CONTINUE. Mostrar resultados de todas las reglas, no sólo una ganadora.
STATE_ONLY bloquea Routing; no revierte Correlación, que ocurre antes, ni elimina auditoría.

## Simulación y aceptación

Evento normalizado Gateway 1.1: eventId,eventKey,tenant.code,resource.name,summary,
lifecycleAction OPEN/CLOSE,effectiveSeverity,timestamps.receivedAt. candidateRules
reemplaza snapshot completo; incluir otras capacidades necesarias para probar interacción.
Omitirlo usa activas. No envía comandos ni escribe grupos. Para recuperar evidencia de
reglas, localizar stages[stage=PolicyEvaluation].evidence: snapshotChecksum,
fieldContractVersion,ruleCount,rule.N.id/version/checksum/match/conditions/proposals,
resolvedDirective. Las trazas no contienen valores originales del evento. Un error
expone failedRuleId/failedRuleVersion/errorCode en lugar de éxito silencioso.

Probar alta, recarga, habilitar, v2 guardada conservando v1, activación v2, 409, desactivar,
retirar e historial. Simular coincidencia/no coincidencia y CONTINUE de prioridad alta
junto a STATE_ONLY de prioridad baja: gana STATE_ONLY. Rechazar severidad string,
campo desconocido y regex no admitida. Verificar tenant y diferenciar fuentes de catálogo.
No declarar ticket creado por una decisión de política ni PASS de navegador sólo con API.
DP-EP-01 conserva seguridad pendiente; no ampliar este incremento a proveedores externos.
