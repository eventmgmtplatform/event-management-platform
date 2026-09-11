# Correlación, auto-suppression y comandos — contrato inicial

> OS_11: el perfil optativo y la coordinación de resultados/cierres se documentan en
> [Lifecycle orchestration](lifecycle-orchestration.md). El alcance inicial descrito abajo permanece para rutas sin ese perfil.

## Transacción y autoridad

Processor conserva grupos/miembros de correlación; Event State Service conserva el
ciclo de vida del evento y los resultados de integración. No se escribe directamente
en `event_management.event_state`. La pertenencia a un grupo es una relación, no
una sustitución de la identidad del evento.

`ProcessingUnitOfWork` serializa por tenant mediante un advisory lock PostgreSQL
con espera acotada. Comprueba replay antes de evaluar y comparte una conexión para
snapshot, correlación, evidencia, ledger de comandos y outbox. Fallar cualquier
escritura revierte el conjunto. El offset Kafka se confirma después del commit.
No hay locks de JVM ni un segundo acceso al pool bajo el lock para leer reglas.

Migraciones aditivas:

- 013: cabecera durable `correlation_group`, con revisión y miembros del ciclo.
- 014: `integration_command`, con el primer envelope inmutable y referencia a su
  procesamiento. Su identidad permanece aunque se limpien salidas ya entregadas.

## Correlación ATTRIBUTE / GROUP

Definición administrada mediante `/api/v1/rules`, usando el schema DA-08:

```json
{"id":"by-node","version":1,"enabled":true,"priority":10,"strategy":"ATTRIBUTE",
 "scope":{"field":"resource.node","operator":"EXISTS"},
 "candidateSelection":{"windowSeconds":300,"maxCandidates":16,"activeOnly":true},
 "match":{"fields":["resource.node"]},"relationship":{"type":"GROUP"},
 "metadata":{"owner":"operations"}}
```

Scope usa el AST tipado DA-04. Match exige 1..4 campos String de resource/enrichment;
no scripts, eventId ni eventKey como sustitutos de correlación. Clave de grupo:
tenant + ID lógico de regla + nombres ordenados de campos + valores. Una versión
nueva con claves equivalentes conserva el grupo; la evidencia registra la versión
exacta evaluada. Cambiar las claves cambia el namespace semántico del grupo.

El primer PROBLEM crea un grupo; otros miembros compatibles se agregan. OK/RESOLVED
actualiza la pertenencia y el grupo resuelve cuando no quedan miembros activos.
Una nueva ocurrencia posterior abre otro ciclo con identidad distinta. La ventana
expira relaciones cuando llega otro evento; no hay barrido autónomo. El límite
inferior temporal es inclusivo. Un ciclo cuyos miembros expiraron también puede
ser reemplazado por otro ciclo; son ciclos de correlación, no ciclos DA-06 del evento.

Los eventos anteriores al último instante del grupo se auditan como tardíos y no
lo modifican. Un PROBLEM con el mismo timestamp no reabre un grupo ya resuelto.
Recuperaciones sin pertenencia previa quedan como ORPHAN_RECOVERY.

Límites iniciales: 8 reglas activas de correlación por tenant, ventana 1..86400
segundos, IDs de evento/clave hasta 256 caracteres y snapshot de hasta 32 miembros
por ciclo. En este subconjunto `maxCandidates` también limita el historial de
miembros retenido en la cabecera del ciclo, incluidas recuperaciones; superar el
límite produce CANDIDATE_LIMIT sin escrituras parciales. Los miembros resueltos no
se usan como relaciones activas. Separar capacidad activa de historial es pendiente
de escalamiento. No se afirma búsqueda histórica general ni soporte topológico.

## Auto-suppression

Registro local de mantenimiento/cambio, separado del blackout:

```json
{"id":"change-1","version":1,"type":"SUPPRESSION","enabled":true,"priority":10,
 "source":"CHANGE","externalStatus":"APPROVED","scope":{"node":"router-1"},
 "schedule":{"timezone":"America/Mexico_City","validFrom":"2026-09-10T10:00:00Z","validTo":"2026-09-10T11:00:00Z"},
 "reason":"Approved maintenance","metadata":{"owner":"operations","externalReference":"change-001"}}
```

MANUAL/MAINTENANCE/CHANGE identifica el origen declarado. ACTIVE/APPROVED habilita
la elegibilidad; CANCELLED/COMPLETED nunca suprime. Se exigen principio y fin
explícitos, zona IANA y scope exacto. No hay consulta síncrona a proveedores ni
verificación externa del estado declarado. Sincronización/importación automática
queda pendiente. Todos los matches retienen ID, versión, referencia, estado y
ventana. SUPPRESS_INTEGRATIONS conserva correlación, evento original y auditoría.

## Routing y comandos

El subconjunto ejecutable admite `SERVICENOW/CREATE_TICKET`, perfil global `default`
del Worker y ciclo de correlación explícitamente seleccionado:

```json
{"id":"group-ticket","version":1,"type":"ROUTING","enabled":true,"priority":10,
 "condition":{"field":"event.severity","operator":"GTE","value":2},
 "actions":[{"type":"CREATE_TICKET","target":"SERVICENOW",
 "parameters":{"configuration":"default","correlationRuleId":"by-node"}}],
 "metadata":{"owner":"operations"}}
```

El payload se limita a resource desde resource.name, summary desde summary y
severity canónica. No hay payload libre ni credenciales. Campos ausentes generan
fallo explícito cuando la ruta es elegible. Sin ciclo aplicable, durante recuperación,
con STATE_ONLY/CORRELATE_ONLY o mantenimiento no se crean comandos. Un evento posterior
al mantenimiento puede crear el comando si el ciclo sigue activo y aún no existe.

La identidad combina tenant, grupo/ciclo, configuración, integración y operación.
Cambiar eventId/processingId/summary no duplica un comando semántico. Rutas repetidas
para el mismo grupo/acción se consolidan; rutas explícitas a grupos distintos pueden
producir 0..N comandos. La primera decisión conserva su envelope íntegro.

Los comandos representan la situación correlacionada: eventId = groupId,
eventKey = `correlation:<groupId>`. metadata conserva sourceEventId/sourceEventKey;
processingId permite consultar la evidencia original. El evento normalizado conserva
su identidad original. El Worker valida y ejecuta; Processor sólo persiste/publica.

GNM, CACF, cierres/actualizaciones, perfiles adicionales y comandos para eventos sin
ciclo de correlación se rechazan o permanecen pendientes según capacidad. No se
inventa un ciclo DA-06 a partir de eventId ni de la versión de configuración.

## Simulación y verificación

`POST /api/v1/simulations` acepta `events` (1..64, IDs únicos), excluyente con event
y evaluatedAt. Usa receivedAt de cada elemento y un snapshot único. El estado de
correlación y el ledger simulado nacen vacíos por petición; no consulta/reemplaza
relaciones de producción. La respuesta lo declara en correlationSource. El mismo
motor se usa en producción y simulación, con estados iniciales explícitamente distintos.

Pruebas cubren concurrencia, replay, rollback, ciclos, tardíos, límites, supresión,
comandos múltiples, deduplicación, retención del primer envelope y validación real
del payload en el Worker. No certifican proveedores reales, paridad histórica,
throughput productivo ni recuperación completa del ecosistema.
