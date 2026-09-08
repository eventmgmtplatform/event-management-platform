# Persistencia, estados y garantías

Migración: infrastructure/postgres/init/008-cacf-core.sql. Requiere esquema
preexistente event_management. Usa BEGIN/COMMIT y ON_ERROR_STOP. CREATE IF NOT
EXISTS permite repetirla, pero no actualiza definiciones de tablas preexistentes.
No incluye down migration ni elimina datos.

| Tabla | Clave / restricciones | Contenido y finalidad |
|---|---|---|
| automation_execution | execution_id UUID PK; command_id, requester_id, provider_execution_id y transaction_number únicos | Identidad, request JSONB, ticket, estado/outcome, timeout/EWT, timestamps, version |
| automation_provider_message | message_id UUID PK; FK execution_id | direction, message_type, transaction_number, http_status, payload TEXT, raw_payload BYTEA, SHA256, duplicate, late_result, created_at |
| automation_result | result_id UUID PK; execution_id único y FK | outcome, requires_review, payload JSONB, created_at |
| automation_outbox | outbox_id UUID PK; sequence_id BIGSERIAL único; execution_id/event_type únicos | payload JSONB, published, created_at, published_at |
| automation_provider_dispatch | PK execution_id/operation; FK ejecución | CREATE/TKTUPDATE, PENDING/IN_FLIGHT/SENT/REVIEW, claimed_at/completed_at |

Los índices parciales cubren deadline activo y outbox pendiente; el índice de
evidencia cubre execution_id/payload_sha256. El SQL incluido es el diccionario
canónico de columnas, tipos, tamaños, defaults y restricciones. version es un
contador de cambios, no un mecanismo de compare-and-swap. Todas las operaciones
JDBC usan prepared statements para valores y transacciones explícitas.

| Estado origen | Disparador | Estado destino |
|---|---|---|
| inexistente | admisión válida | RECEIVED |
| RECEIVED | claim CREATE | SUBMITTING |
| SUBMITTING | respuesta 2xx con XML seguro | SUBMITTED |
| ejecución activa | Acknowledge_Create válido | IN_PROGRESS |
| ejecución activa | callback distinto de ACK | COMPLETED con outcome normalizado |
| ejecución activa sin ACK | fallo de envío CREATE | SUBMISSION_FAILED / UNKNOWN |
| ejecución activa | plazo vencido | TIMED_OUT / TIMEOUT |
| terminal | callback posterior | sin cambio; evidencia tardía |

Deadline de resultado: accepted_at + result_timeout_seconds, persistido una sola
vez. Antes de procesar callback se verifica deadline con el reloj SQL, aunque el
watcher aún no haya corrido. Sin ACK, se usa submitted_at + httpTimeout + ackTimeout.
EWT no modifica el plazo. Un callback exactamente repetido se identifica por SHA256
sobre los bytes; una representación distinta no es duplicate por hash, pero sigue
respetando el estado terminal y el deadline ya establecido.

| Señal NEXT (case-insensitive) | Outcome | Acción terminal ITSM |
|---|---|---|
| RESOLVE / REMEDIATION | REMEDIATED | nota, sin cerrar ni reasignar |
| ESCALATE | ESCALATED | reasignación al grupo original + nota |
| TRANSFER / TOWER TRANSFER | TRANSFER | reasignación + nota |
| NO ACTION | NO_ACTION | reasignación + nota |
| NO MATCHING HOST | NO_MATCHING_HOST | reasignación + nota |
| REPEATED INCIDENT / AUTO ESCALATION | REPEATED_INCIDENT | reasignación + nota |
| vencimiento interno | TIMEOUT | reasignación + nota |
| desconocido | UNKNOWN | sin acción terminal, requiresReview=true |

WorkflowSubStatus NO MATCHING HOST tiene prioridad; de lo contrario se usa
WorkflowStatus, o TransactionName si el primero está vacío. No hay interpretación
estructurada de returnCode, closureCode o salida de automatización: permanece en
la evidencia original. Un callback válido desconocido termina la ejecución con
UNKNOWN. La asignación inicial holding puede haberse ejecutado antes de UNKNOWN.

La unicidad de resultados es SQL; la entrega Kafka es al menos una vez. La
foundation ServiceNow protege la ejecución con su ledger. En RECONCILE la acción
CACF evita PATCH y solicita revisión mediante error; no existe reparación
automática de una mutación incierta. No hay endpoint de reintento/limpieza CACF.
