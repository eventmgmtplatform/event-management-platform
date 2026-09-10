# Configuración versionada y contratos

## Alcance implementado

El primer incremento implementa el envelope `rule-v1`, validación JSON Schema y
semántica, AST tipada y evaluación de reglas `POLICY` y planes `ENRICHMENT`. Los otros ocho tipos del
schema están reservados y se rechazan como `RULE_TYPE_NOT_IMPLEMENTED` hasta que
sus capacidades existan. No se presenta una regla aceptada pero inoperante.

El `$id` relativo del schema suministrado se normaliza a
`urn:event-management:rule-v1`; el vocabulario y estructura no cambian. El schema
se carga localmente. El compilador rechaza JSON duplicado, contenido posterior al
objeto, campos desconocidos y valores incompatibles antes de admitir una versión.

## Campos, operadores y acciones

Campos originales conservados en `policy-fields-v2`:

| Campo | Tipo / origen |
|---|---|
| event.identifier | String / eventId canónico |
| event.key | String / clave conservada del Gateway |
| event.status | String / PROBLEM, OK, RESOLVED |
| event.severity | Decimal / severidad canónica 0..5 |
| event.receivedAt | Instant / timestamp recibido del Gateway |
| tenant.customerCode | String / tenant del envelope aceptado |

No se resuelven rutas mediante reflexión. Se agregan resource.node, resource.component
y los hechos tipados descritos en [enrichment e inventory](enrichment-and-inventory.md).
El namespace de estado aún no está habilitado.

Se soportan los 15 operadores del diseño, ALL/ANY/NOT y comparaciones temporales
normalizadas. IN/NOT_IN exige colección homogénea; BETWEEN tiene exactamente dos
límites ordenados e inclusivos. Las comparaciones ordenadas exigen número o
Instant. EXISTS/NOT_EXISTS no acepta value. Ningún operador convierte strings a
números. Los campos de este primer contrato son obligatorios; el evaluador reserva
NO_MATCH para un valor ausente en predicados ordinarios. NOT niega el resultado de
su condición hija; no introduce coerciones de valores ausentes.

REGEX usa coincidencia de cadena completa: máximo 128 caracteres, sin grupos,
alternancia, escapes/backreferences ni cuantificadores entre llaves; como máximo
un cuantificador simple. Entrada máxima 4096 caracteres. El exceso en runtime
produce error explícito y DLQ sanitizada, no un NO_MATCH silencioso.

Acciones admitidas: CONTINUE, SUPPRESS_INTEGRATIONS, STATE_ONLY, CORRELATE_ONLY.
Son directivas, no ejecución de integraciones ni una implementación del algoritmo
de correlación. Se rechazan target y parámetros no vacíos hasta disponer de su
capacidad. Metadatos admitidos: owner, description y tags; extensiones arbitrarias
no se habilitan. No se aceptan scripts ni estructuras de credenciales.

Límites iniciales del contrato: 64 Ki caracteres JSON, profundidad 32, 1024 nodos
JSON, 128 elementos por colección, 16 acciones y 256 reglas activas por tenant.
La biblioteca limita además anidamiento de parseo a 40. No son SLO productivos.

## Persistencia y evaluación

Migración aditiva `011-processor-rule-registry.sql`:

- rule_definition: identidad (tenant, rule_id), revisión y versión activa.
- rule_version: JSON normalizado, SHA-256, actor, motivo y fecha inmutables.
- rule_change: historial de creación/activación/desactivación/retiro inmutable.

Los triggers impiden UPDATE/DELETE sobre versiones e historial. Foreign keys
impiden activar referencias inexistentes o de otro tenant. Tenant es ámbito del
registro, separado del JSON cerrado rule-v1; nunca se toma de metadata.owner.
La administración interna exige tenant, actor y motivo no vacíos. No equivale a
autenticación: REST está habilitado sin identidad/RBAC por decisión expresa del usuario.

Crear versión valida y exige el siguiente entero. `enabled:true` en una definición
sólo permite habilitarla; crearla no modifica la versión activa. La activación
requiere revisión esperada y revalida checksum/semántica. Reemplazar o deshabilitar
no altera versiones previas; rollback habilita explícitamente una versión anterior.
RETIRED es terminal para la identidad. Los bloqueos PostgreSQL serializan escritores;
la activación y auditoría se comprometen juntas. No hay autoridad en locks JVM.

Cada evento obtiene un snapshot con un único SELECT de versiones activas de su
tenant. Se ordena por prioridad descendente, ID y versión ascendentes. Mantiene
todos los resultados y propuestas; DirectiveResolver aplica precedencia explícita,
sin last-write-wins. La evidencia guarda checksum del snapshot, contrato de campos,
ID/versión/checksum de cada regla y trazas de predicados sin valores del evento.

Un snapshot capturado no cambia durante el pipeline. Simulación usa el mismo
compilador/evaluador y no publica ni escribe. Una indisponibilidad del registro
impide confirmar Kafka; no se evalúa silenciosamente con reglas vacías. Eventos
Gateway v1.0 sin tenant conservan el flujo compatible, con PolicyEvaluation
SKIPPED/TENANT_REQUIRED_FOR_RULES y sin acceso a reglas de clientes.

## Fronteras de compatibilidad

Gateway v1.0/v1.1 y la salida normalizada conservan su contrato. El marcador de
enriquecimiento PENDING_RULES permanece hasta completar ese proceso. El detalle
Processor informa INCREMENTAL y checksum de configuración.

WorkerCommandAdapter traduce la intención al envelope real del Worker:
`integrationType`, `eventKey`, `tenant`, `operation`, `payload` y commandId.
El diseño DA-09 usa `integration`, mientras BASELINE usa target anidado; ninguna
forma se envía directamente al consumidor. El adaptador conserva processingId,
createdAt, configuration y metadata.idempotencyKey como campos adicionales.

Operaciones reconocidas del consumidor actual: SERVICENOW/CREATE_TICKET,
GNM/SEND_NOTIFICATION y CLOSE_NOTIFICATION, CACF/AUTOMATION_REQUESTED. Esto no
certifica payload específico ni disponibilidad del proveedor. No se conectó el
adaptador a emisión: RoutingDecision y CommandGeneration siguen pendientes.

La identidad de comando incluye tenant, eventKey, ciclo, destino, integración y
operación. No depende únicamente de processingId. El Worker exige igualdad del
JSON completo al reutilizar commandId: la futura generación debe conservar la
primera intención durable y reutilizarla, incluidos eventId/processingId/createdAt.
Cambiar esos campos o payload bajo el mismo ID produce colisión. El adaptador no
sustituye la persistencia de esa primera intención ni decide cuándo comienza un ciclo.

DLQ incorpora los campos BASELINE conservando los campos operativos anteriores.
`originalEvent` es un objeto redactado con payloadHash; no permite replay directo.
Para entrada inválida eventId vacío expresa identidad desconocida (el schema lo
permite). Topic/partition/offset y hash permiten localizar el origen autorizado.
No se duplica el input inválido en logs, evidencia ni DLQ.

## Operación y pruebas

Aplicar 009, 010 y 011 antes de arrancar la imagen nueva en una base existente.
Readiness exige tablas de reglas; no hay migración destructiva ni cambio de offsets.
Una imagen anterior puede convivir con las tablas aditivas durante rollback, pero
no evalúa reglas: ese retroceso necesita considerar la pérdida de capacidad.

RuleRegistryTest crea y elimina exclusivamente su base temporal en PostgreSQL de
laboratorio; prueba migración repetible, escritores concurrentes, aislamiento,
activación, rollback, inmutabilidad y evidencia histórica frente a replay.
RuleCompilerTest cubre tipos, operadores, complejidad, regex y determinismo.
WorkerContractTest y el consumidor real comparten un fixture de envelope.

Este incremento no certifica release v1.0.0 completo. Administración REST/RBAC,
reglas especializadas, fuentes externas de enrichment, lifecycle/correlación, emisión de comandos,
paridad histórica y gates operativos restantes permanecen PENDING.
