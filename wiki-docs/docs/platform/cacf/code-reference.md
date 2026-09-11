# Referencia de código

Rutas relativas a la raíz del repositorio o a source/ en el PKC.

## AutomationRequest

parse valida y copia el request, aplica límites e identidades. text valida campos textuales. Record canónico sin terminología XML.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationRequest.java`.

## AutomationResource

submit/get/ticket exponen el ciclo REST. legacy/callback comparten admisión XML. authorize valida activación y token; metrics exporta gauges. No realiza HTTP al proveedor.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationResource.java`.

## AutomationRepository

Propietario de transacciones JDBC, correlación y locks. Ver detalle de métodos debajo.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationRepository.java`.

## NextAdapter

requesterId compone identidad; create/ticketUpdate construyen DOM escapado; parse valida raíz y campos; Message.acknowledgement/outcome normalizan señales. Helpers document/transaction/add/serialize encapsulan XML.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/NextAdapter.java`.

## SecureXml

parse limita bytes, profundidad 64, DTD/entidades externas/XInclude y sanitiza errores sin adjuntar causa del parser. Devuelve DOM namespace-aware.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/SecureXml.java`.

## NextHttpClient

send selecciona ruta, valida URL, agrega Basic, limita tiempo y bytes. Response contiene status/body. BoundedBody cancela recepción sobredimensionada. No redirects ni retries.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/NextHttpClient.java`.

## CacfSettings

Constructor CDI Singleton valida límites y credenciales al activar. Campos finales compartidos; no es un proxy ApplicationScoped.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/CacfSettings.java`.

## CacfRuntime

start registra tres schedulers; dispatch toma intención y guarda solicitud/respuesta; publish drena outbox hacia tópico según tipo; guard registra tipo de error y permite siguiente pasada; stop interrumpe ejecutor.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/CacfRuntime.java`.

## CacfKafkaRoute

configure crea ruta solo si CACF está activo; distingue proveedor, invoca validación común/admisión, gestiona DLQ y commit manual.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/CacfKafkaRoute.java`.

## CacfCommandProcessor

process comprueba activación y operación, construye AutomationRequest, compara eventId/tenant y llama accept.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/CacfCommandProcessor.java`.

## CacfReadinessCheck

call devuelve up cuando CACF está deshabilitado; activo exige consultas SQL y ruta iniciada.

Fuente: `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/CacfReadinessCheck.java`.

## Métodos y fronteras de AutomationRepository

| Método | Efecto / frontera |
|---|---|
| transaction / update / bind / row | conexión, commit/rollback, parámetros y lectura con lock opcional |
| get | lectura transaccional por UUID |
| accept | insert idempotente, igualdad de request/command/key, dispatch CREATE, holding |
| associateTicket | lock ejecución, asociación inmutable, agenda update/acción según estado |
| claimDispatch | SKIP LOCKED sobre dispatch y transición previa al HTTP |
| recordRequest | evidencia saliente en transacción propia |
| recordDispatch | evidencia entrante, SENT/REVIEW, SUBMITTED o UNKNOWN de envío |
| callback | correlación, lock, dedup por hash, tardíos, deadline, ACK o terminal |
| enqueueUpdate | una intención TKTUPDATE cuando están ambas identidades |
| expire | REVIEW para claims vencidos y TIMEOUT en lotes de hasta 100 |
| complete | estado terminal + resultado único + outbox + acción ITSM atómicos |
| ticketCommand | payload foundation holding/result; evita acción UNKNOWN |
| outbox | inserción única ejecución/tipo |
| publishOne | envío del primero global con lock; commit de published después |
| evidence / hash | bytes originales, representación UTF-8, SHA256 y flags |
| metrics | seis consultas count dentro de una transacción |

Dispatch encapsula UUID, operación y snapshot de ejecución. Publisher es el puerto
funcional invocado bajo transacción para publicar. Conflict extiende
IllegalArgumentException y permite traducir REST a 409 y Kafka a comando inválido.

## Dependencias ServiceNow modificadas

| Clase/archivo | Cambio de esta generación |
|---|---|
| IntegrationProvider | agrega CACF |
| ServiceNowHttpInvoker | método default updateTicket, no soportado si implementación no lo define |
| CamelServiceNowHttpInvoker | PATCH por sys_id validado, timeout y cuerpo JSON |
| ServiceNowLookupClient | puerto findByTicketNumber |
| CamelServiceNowLookupClient | búsqueda por number validado, reutiliza parser de lookup |
| ServiceNowAutomationActionProcessor | RECONCILE evita mutación; lookup existente, REASSIGN/ADD_WORK_NOTE, PATCH y respuesta para procesador común |
| routes/integration-worker.xml | CACF se detiene en consumidor legado; nueva rama ServiceNow APPLY_AUTOMATION_RESULT con ledger |
| application.properties | variables CACF y defaults |

Se reutilizan IntegrationCommandProcessor, IntegrationResultProcessor y el ledger
existente. El snapshot contiene el worker completo para compilar esas dependencias;
no significa que todas sus clases hayan cambiado en CACF. Los archivos alterados
se enumeran en el manifiesto de procedencia del PKC.

## Pruebas y extensiones

AutomationRepositoryTest requiere PostgreSQL cacf_test y worker detenido. Los
fixtures generan identidades propias; NextAdapterTest verifica XML y outcomes;
SecureXmlTest verifica rechazos; NextHttpClientTest prueba transporte local;
ServiceNowAutomationActionProcessorTest prueba PATCH y guard RECONCILE. La suite
completa del worker proporciona cobertura de regresión de dependencias existentes.

Para ampliar una señal NEXT, cambiar Message.outcome y sus pruebas y revisar la
política ticketAction en complete. Para ampliar un campo, revisar validación,
mapeo XML, persistencia y contrato de resultado conjuntamente. Para una migración
posterior crear un nuevo SQL incremental: IF NOT EXISTS no altera columnas viejas.
No agregar retries HTTP sin un mecanismo certificado de reconciliación.
