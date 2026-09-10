# Contratos de código y de integración

> OS_11: el perfil optativo y la coordinación de resultados/cierres se documentan en
> [Lifecycle orchestration](../event-processor/lifecycle-orchestration.md). El alcance inicial descrito abajo permanece para rutas sin ese perfil.

## API REST

Todas las rutas CACF requieren X-CACF-Token; deshabilitado devuelve 404 y token
inválido 401. Los errores de validación explícitos contienen JSON {"error":"..."}.
Errores inesperados de infraestructura se propagan como errores del servidor.

| Método y ruta | Entrada | Éxito | Errores explícitos |
|---|---|---|---|
| POST /api/v1/automations | JSON canónico; Idempotency-Key igual al UUID executionId | 202, executionId/state/accepted | 400 inválido, 409 identidad en conflicto |
| GET /api/v1/automations/{id} | UUID | 200, vista de ejecución | 400 UUID, 404 ausente |
| PUT /api/v1/automations/{id}/ticket | {"number":"INC..."} | 204 | 400 inválido, 404 ausente, 409 ticket distinto |
| POST /data | text/xml o application/xml | 200 text/plain: request is applied | 400 XML, 404 correlación ausente, 409 conflicto, 413 tamaño |
| POST /api/v1/providers/next/callback | mismo callback | mismo ACK | mismos errores |
| GET /metrics | token | 200 texto de gauges | autenticación |

GET expone executionId, eventId, eventKey, providerExecutionId, itsmTicketNumber,
state, outcome, requestedAt, acceptedAt, deadlineAt, completedAt, provider=NEXT y
requiresReview=(outcome==UNKNOWN). Los campos temporales no establecidos son null.
No devuelve request ni XML original. La asociación al ticket es inmutable; repetir
el mismo número es válido. El resultado almacenado conserva una copia del ticket
original del request; una asociación posterior se refleja en la ejecución y en
los comandos ITSM, no reescribe el resultado histórico.

## Request canónico schemaVersion 1.0

| Campo | Regla implementada |
|---|---|
| executionId | UUID textual |
| eventId | texto obligatorio, máximo 100 |
| customer.code | texto obligatorio, máximo 64, sin dos puntos |
| event.sourceSystem / sourceSerial | textos obligatorios, máximo 200 cada uno, sin dos puntos |
| event.summary | texto obligatorio, máximo 10000 |
| event.resourceId | texto obligatorio, máximo 255 |
| event.severity | entero no negativo representable como int |
| ticket.originalAssignmentGroup / holdingAssignmentGroup | obligatorios, máximo 255 |
| ticket.number | opcional al ingresar; columna SQL máximo 100; PUT lo valida explícitamente |
| automation.provider | NEXT |
| automation.resultTimeoutSeconds | entero 1–604800; default de configuración si ausente |

El request se copia antes de persistir. No es una validación JSON Schema exhaustiva:
campos adicionales se conservan. Los getters de texto validado aplican trim, pero
no normalizan todo el árbol JSON. correlationId del fixture no se propaga como
campo de resultado. eventKey del envelope tiene máximo 128, con fallback eventId;
event.eventKey es un campo distinto usado por la descripción NEXT.

El fixture reproducible está en ../../testing/services/integration-worker/resources/cacf/request.json.

## Kafka

Envelope de ingreso: commandId, eventId, eventKey, tenant, integrationType=CACF,
operation=AUTOMATION_REQUESTED, payload=request. commandId admite hasta 128;
eventId y tenant deben coincidir con payload.eventId y payload.customer.code.
El validador compartido IntegrationCommandProcessor se ejecuta antes de la
admisión CACF. Grupo cacf-admission, earliest, autoCommit=false, manualCommit=true.
Tras commit SQL se confirma offset. Un CACF inválido se publica a events.dlq con
integrationType, errorCode=INVALID_AUTOMATION_COMMAND, commandId y message y luego
se confirma. JSON ilegible o mensajes de otros proveedores se omiten y confirman.
Errores SQL no se capturan como validación y no confirman el offset.

Resultado schemaVersion 1.1: resultId y messageId idénticos, messageType
AUTOMATION_COMPLETED, executionId, commandId, eventId, eventKey, tenant,
integrationType=CACF, operation=AUTOMATION_REQUESTED, provider=NEXT, externalId,
providerExecutionId, status, state, outcome, requiresReview, completedAt y ticket.
Con callback terminal añade providerOutcome y providerSubstatus. UNKNOWN no añade
ticketAction. Los demás añaden action, assignmentGroup, addWorkNote y source.
**status es FAILED solo para TIMEOUT; los demás, incluido UNKNOWN y el resultado
de SUBMISSION_FAILED, actualmente publican SUCCESS.** Consumidores deben evaluar
state/outcome/requiresReview; no interpretar SUCCESS como remediación confirmada.

Comandos ITSM: integrationType=SERVICENOW, operation=APPLY_AUTOMATION_RESULT,
commandId={executionId}-holding o -result; payload.ticketNumber, action,
assignmentGroup y workNote. El transporte del outbox usa acks=all y key=eventKey.

## NEXT XML

Namespace ServiceIncident: http://b2b.ibm.com/schema/IS_B2B_CDM/R2_2.
Se declara schemaLocation, pero no se valida contra un XSD externo. HTTP Basic,
text/xml UTF-8, POST CREATE a /tupix/api/v1/netcool/tickets y TKTUPDATE a
/tupix/api/v1/netcool/incidents. No se siguen redirects ni se reintentan mutaciones.
RequesterID=sourceSystem:sourceSerial:customerCode. TransactionNumber de CREATE
es executionId; TKTUPDATE usa executionId-ticket. TransactionType=2 y fecha Instant.
CREATE envía RequesterSeverity, TradingPartnerID, TransactionRouting
ASYNC::{customerCode}INC, Incident.Abstract/Description, AssignedTo.AssignedGroup,
Asset.AssetID/Asset_Tag y FlexFields. TKTUPDATE envía ProviderID y ticket en ipc.

Los labels de Description, en orden, son Summary, Date, Severity, ResourceId,
CustomerCode, InstanceId, InstanceSituation, AlertKey, TicketGroup, InstanceValue,
ComponentType, Component, SubComponent, ApplId, Node, NodeAlias, AlertGroup,
EventType, MonitoringSolution y EventKey. Opcionales ausentes producen texto vacío.

| FlexField mappedTo | event.campo |
|---|---|
| originating_event_id | sourceSerial |
| event_class | eventClass |
| service_state / host_state | serviceState / hostState |
| ITEM / tec_id | item / sourceSystem |
| severity / ipcenabled | severity / automationEnabled |
| fqdn / ipaddress | node / nodeAlias |
| alertkey / alertgroup | alertKey / alertGroup |
| component / componenttype / subcomponent | component / componentType / subComponent |
| instanceid / instancevalue / instancesituation | instanceId / instanceValue / instanceSituation |
| monitoring / location / lastoccurrence | monitoringSolution / location / occurredAt |

Callback: raíz y namespace exactos, TransactionName requerido; RequesterID,
ProviderID, TransactionNumber, WorkflowStatus y WorkflowSubStatus opcionales para
parseo, sujetos a correlación posterior. Campos con más de una aparición se
rechazan. estimated_wait_time se conserva como texto informativo. ACK requiere
ProviderID nuevo o ya conocido. La correlación busca las tres identidades; si
identifican ejecuciones diferentes devuelve conflicto. RequesterID y ProviderID
conocidos deben coincidir. Un número de transacción desconocido no basta para
correlacionar, pero no se compara estrictamente si otra identidad sí encuentra
la ejecución. Los bytes de callbacks aceptados se conservan íntegros.
