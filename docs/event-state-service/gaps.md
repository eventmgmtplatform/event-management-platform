# ESS-01: situación actual frente a V1

La clasificación inicial se basa en el código en `887baeaf` y la inspección
`evidence/os-05-ess/ESS-01`. Las correcciones de esta rama se indican por separado.

| Capacidad | Baseline | Evidencia / diferencia |
|---|---|---|
| Consumer integration.results | PRESENT_VALIDATED | `IntegrationResultStateProcessor`, XML `integration-results-state-route` |
| PostgreSQL autoritativo | PRESENT_VALIDATED | `EventStateRepository`, DDL 002/003 |
| Duplicados idénticos / colisión resultId | PRESENT_VALIDATED | Claim + comparación JSONB; pruebas IT |
| Alta concurrente del mismo eventKey | PARTIAL | FOR UPDATE no bloquea una fila inexistente; corregido con advisory transaction lock |
| Atomicidad ante excepción checked | PARTIAL | JTA sin rollbackOn; corregido y probado con fallo SQL después del claim |
| Identidad / tenant | PARTIAL | PK global event_key; ahora rechaza placeholders y colisiones entre tenants; no equivale a PK tenant/eventKey |
| Proyección OpenSearch | PRESENT_VALIDATED | PUT posterior al commit DB; replay vuelve a proyectar |
| Ordenamiento / versión externa OS | ABSENT | Campo version informativo; PUT no tiene control de versión externa |
| events.state.requested | ABSENT | Processor publica normalized y commands, ESS sólo consume resultados |
| OPEN/CLOSE/reopen de ESS | ABSENT | newState fija OPEN; no hay intérprete de transiciones |
| events.lifecycle | ABSENT | Topic creado en Kafka; no existe productor ESS |
| Outbox ESS / transition history | ABSENT | No confundir outbox del Processor con uno de ESS |
| Ledger genérico integration_execution | PARTIAL | JSON integrations extensible en estructura, switch restringido a SERVICENOW/GNM/CACF |
| Severidad dual / tally / relaciones | ABSENT | No hay esas columnas o lógica en ESS |
| DLQ / quarantine ESS | ABSENT en baseline; PARTIAL ahora | Cuarentena PostgreSQL con body/hash/topic/partition/offset; sin publicación de topic de errores |
| Reconciliación / rebuild / API consultas | ABSENT | Sin recursos REST de estado ni jobs de reconciliación |
| Health CLI | PRESENT_VALIDATED | emctl administra ESS, Docker y `/health/ready` |
| Observabilidad | PARTIAL | Logs y datasource health; faltan métricas de outbox, drift, duplicate/stale y salud explícita de consumer |
| Framework | PRESENT_VALIDATED | Java 21, Quarkus 3.38.0, Camel 4.21.0 resuelto y observado en pruebas |

El campo `eventId` se reemplaza con el último resultado nuevo y la clave primaria
no incluye tenant. Esta rama evita transferencia entre tenants, pero no permite
dos tenants con la misma clave funcional: ese cambio exige migración compatible.
Los estados SUCCESS son el contrato actual; SUCCEEDED, messageId y tenantId de
la arquitectura son objetivo, no aliases implícitos.

PostgreSQL commit → OpenSearch PUT → Kafka manual commit es una escritura dual.
Si OpenSearch falla, el estado/claim pueden existir y Kafka no se confirma; el
replay obtiene el estado más reciente sin incrementar versión. No hay outbox ni
reparación independiente. Mensajes fuera de orden pueden reemplazar el resultado
del proveedor; la prueba de duplicados no certifica protección contra stale.

Siguiente checkpoint: ESS-02/03, congelar contrato de transición explícita,
identidad tenant/eventKey, ordering y estados; después migraciones compatibles,
outbox, reconciliación y API. La suite conserva esos checks como pendientes y
no presenta el éxito del flujo existente como certificación completa de V1.

Hallazgo reproducido en runtime: offset 1348 de integration.results/0 bloqueaba
repetidamente por ausencia de resultId aunque Docker/HTTP estaban saludables.
La corrección clasifica únicamente rechazos permanentes de contrato/identidad;
conserva el mensaje en ess_quarantine y confirma tras el commit. Un fallo de DB
o proyección sigue sin confirmación. No se resetearon offsets. El contenido de
cuarentena puede contener datos del evento: no exportarlo a logs ni a Git.
