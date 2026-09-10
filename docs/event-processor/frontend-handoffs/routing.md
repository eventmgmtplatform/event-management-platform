# Prompt para frontend — Routing y comandos base

Conectar administración y simulación de reglas ROUTING a la consola existente.
Reutilizar proxy y cliente de Blackouts y controles de Correlación. No modificar otros
motores ni incorporar seguridad. Certificar desde navegador; pruebas API no sustituyen
esta aceptación. Los comandos son resultados inmutables del motor, no registros CRUD
editables ni un botón para enviar solicitudes arbitrarias al Worker.

## Punto de partida y entrega

Este es el prompt completo para ejecutar en el chat de frontend. Backend base
certificado en ca5eaa8; recorrido integrado certificado en a78b337. El alcance
mínimo y las dos revisiones diferidas están registrados en 39be64d. Consultar
`docs/event-processor/routing-backend.md` y `minimum-v1-checklist.md` si hace falta
contrastar una capacidad. No asumir que una intención de comando prueba éxito remoto.

Revisar y reutilizar los archivos actuales de la consola:

- `services/event-management-console/src/modules/blackouts/blackouts.api.ts`: cliente
  administrativo, ETag, mutaciones e historial.
- `services/event-management-console/src/modules/policy/ConditionEditor.tsx` y
  `PolicySimulation.tsx`: condiciones tipadas y presentación de simulación.
- `services/event-management-console/src/modules/correlation/CorrelationSimulation.tsx`
  y `correlation.model.ts`: secuencias y definiciones de Correlación.
- Router y registro de navegación existentes; integrar la entrada Routing/comandos
  con los patrones actuales, traducciones español/inglés y tema de la consola.

Implementar el módulo mínimo bajo `src/modules/routing/`. Conservar cambios de los
otros chats; no reemplazar router, traducciones o navegación completos. Revisar el
estado de Git antes de editar archivos compartidos. No crear otro cliente HTTP,
backend o catálogo si los existentes cubren este contrato.

## API existente

Proxy same-origin `/api/processor/v1` hacia `event-processor:8082/api/v1`.
Enviar X-Tenant-Id explícito y X-Actor-Id como metadata local no autenticada. No usar
localhost del navegador ni SQL. No hay endpoint dedicado `/routing` o `/commands`.

| Acción | Ruta relativa |
|---|---|
| Listar resúmenes mixtos | GET `/rules?limit=50&after=<cursor>` devuelve items,next |
| Leer última definición | GET `/rules/{id}` devuelve rule,checksum,revision,status,latestVersion,activeVersion y ETag |
| Leer versión específica | GET `/rules/{id}?version=N` |
| Historial | GET `/rules/{id}/history?limit=50&after=0`, next es cursor de revisión |
| Validar | POST `/rules/validate` con `{rule}` |
| Guardar versión | POST `/rules` con `{rule,reason}` |
| Activar/desactivar/retirar | POST `/rules/{id}/enable`, `/disable`, `/retire` con `{version,reason}` |
| Simular | POST `/simulations` con `{event,candidateRules?}` o `{events,candidateRules?}` |
| Auditar evento real | GET `/explain/{processingId}` |

La lista no incluye tipo: cargar detalles y seleccionar `rule.type === 'ROUTING'`.
Consumir next hasta null, sin detenerse ante una página sin rutas. Acotar concurrencia.
GET sin version muestra la última, que puede diferir de la activa. Selector de grupo:
usar reglas con strategy, mostrar si están habilitadas; no inferir capacidad por ID.
IDs comparten namespace por tenant. La API valida sintaxis de correlationRuleId, no
exige existencia/activación de esa referencia al guardar; advertirlo al usuario y
permitir comprobarlo mediante simulación.

Mutaciones requieren If-Match con ETag actual, o `"0"` para alta, e Idempotency-Key por
acción lógica. Conservar clave/cuerpo/actor/revisión ante reintento incierto. Recibo
201 de alta: ruleId,version,revision,status,requestId. Manejar 409 refrescando sin
sobrescribir, 422 validación, 428 precondición, 404 y 503. No confundir recibo CREATED
con estado ENABLED. Guardar nueva versión latestVersion+1 no activa; la anterior sigue
hasta habilitar la nueva. Mostrar éxito parcial guardar/activar. Desactivar/retirar
usa activeVersion si existe o latestVersion. Retiro terminal, sin DELETE ni reutilizar ID.

## Formulario mínimo

```json
{
  "id":"group-ticket","version":1,"type":"ROUTING","enabled":true,"priority":10,
  "condition":{"field":"event.severity","operator":"GTE","value":2},
  "actions":[{"type":"CREATE_TICKET","target":"SERVICENOW",
    "parameters":{"configuration":"default","correlationRuleId":"by-node"}}],
  "metadata":{"owner":"operations"}
}
```

Reutilizar AST tipado para condition; validar en backend. En mínimo ofrecer una acción
CREATE_TICKET/SERVICENOW/default y selector de regla de correlación del mismo tenant.
No ofrecer payload libre, credenciales, perfiles inventados, CLOSE_TICKET ni GNM como
acciones de este formulario. El perfil optativo lifecycle existente tiene contrato y
flujo separados: preservarlo si se detecta en una definición; no quitarlo silenciosamente
al guardar. Mantener esas reglas fuera de edición simplificada hasta soporte explícito.

## Decisión y vista de comandos

Payload procede de resource.name → resource, summary y severidad canónica. Requiere
un grupo activo aplicable y una condición coincidente. Supresión/blackout y directivas
STATE_ONLY/CORRELATE_ONLY impiden comandos. Recuperación no crea comandos en perfil
base. Guardar/activar no reprocesa eventos históricos.

La identidad semántica combina tenant, grupo/ciclo, configuración, integración y operación.
Rutas equivalentes o nuevos eventos del mismo ciclo no duplican el comando; cambiar
versión de ruta tampoco fuerza reenvío. El primer envelope permanece inmutable.
No implementar «reintentar» cambiando ID ni edición/borrado de comandos desde la UI.

Mostrar simulation.candidates y routing.decisions con ruleId,ruleVersion,
correlationRuleId,reason,commandId. Razones relevantes: COMMAND_ELIGIBLE,
EXISTING_SEMANTIC_COMMAND, NO_CORRELATION_CYCLE, CONDITION_NO_MATCH,
RECOVERY_OPERATION_NOT_IMPLEMENTED, DIRECTIVE_SUPPRESS_INTEGRATIONS,
COMMAND_PAYLOAD_FIELDS_REQUIRED. Explain contiene routing.commands y routing.decisions;
una intención o publicación no prueba éxito del proveedor. No mostrar «ticket creado»
a partir de COMMAND_ELIGIBLE. No inventar endpoint de estado del Worker.

candidateRules reemplaza el snapshot completo: incluir regla de Correlación y ruta
para probar el borrador. Omitirlo usa reglas activas. Las simulaciones comienzan con
estado/ledger vacíos por petición, no consultan producción ni envían al proveedor.
Para deduplicación en secuencia usar events con eventIds únicos, mismo resource.name,
eventKeys por miembro y receivedAt creciente. Normalizado 1.1 usa lifecycleAction
OPEN/CLOSE y effectiveSeverity. No enviar event y events a la vez.

## Aceptación desde navegador

Probar alta, persistencia tras recarga, activación, v2 guardada sin sustituir v1,
conflicto 409, activación v2, desactivación, retiro e historial. Simular grupo+ruta con
un candidato; ruta sin grupo con cero; secuencia de dos miembros con un comando
semántico; recuperación sin CREATE; configuración inválida rechazada. Elegir tenant
antes de escritura. Conservar evidencia HTTP y capturas en evidences.

## Entrega verificable para cerrar frontend

Al terminar, dejar `services/event-management-console/validation-routing.md` con:

- Resultado PASS/FAIL/PENDING por caso de aceptación, URL/ruta desplegada y versión
  o commit probado. Distinguir interacción real de navegador y corroboración HTTP.
- Enlaces a `evidences/routing/frontend-<fecha>/`: respuestas, capturas y escenarios
  sintéticos. No agregar esas evidencias generadas a commits.
- Comprobación de persistencia tras recarga y de que una versión guardada no sustituye
  a la activa; resultado del conflicto entre dos pestañas, conservando el borrador.
- Secuencia simulada con dos miembros y un único comando semántico, recuperación sin
  CREATE y ruta sin correlación sin comando. No enviar tickets reales para probar UI.
- Manejo de perfil lifecycle existente sin pérdida de parámetros ni edición simplificada
  destructiva; campo inválido/503 visible y selección obligatoria de tenant.
- Validación TypeScript/build y pruebas pertinentes del módulo, con resultado explícito;
  cobertura visual en español/inglés y con la apariencia actual.
- Limpieza de reglas sintéticas (desactivar/retirar las propias) y relación de archivos
  cambiados. Publicar sólo el alcance propio si el chat tiene autorización de Git.

Actualizar el índice de handoffs únicamente cuando exista esa evidencia. Si un caso
no puede ejecutarse, marcarlo PENDING con causa; no convertir la existencia del módulo
en aceptación. La consolidación en main, tag y entrega formal se realizará después
con el conjunto final de cambios; no mezclar trabajos ajenos en el commit del módulo.

DP-EP-01/04/06/08: seguridad, revisión final DA-06, extensiones y backup/restore con
replay permanecen diferidos. No ejecutar esas revisiones como requisito de este formulario.
La integración a ServiceNow real y el perfil Lifecycle no se certifican con este formulario.
