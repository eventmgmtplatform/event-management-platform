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
| events.state.requested | PRESENT_VALIDATED en laboratorio | Outbox Processor, StateRequestProcessor, DDL 017; OS_11 |
| OPEN/CLOSE/reopen de ESS | PRESENT_VALIDATED en pruebas ESS | StateTransitionRepository; UC-001 cubre apertura/recuperación, no todos los ciclos de reapertura |
| events.lifecycle | ABSENT | No existe productor ESS |
| Outbox ESS / transition history | PARTIAL | Historial ess_event_transition implementado; outbox propio ESS ausente |
| Ledger genérico integration_execution | PARTIAL | JSON integrations extensible en estructura, switch restringido a SERVICENOW/GNM/CACF |
| Severidad dual / tally / relaciones | PARTIAL | DDL 017 y transiciones con severidad/tally; fuente y situación separadas |
| DLQ / quarantine ESS | ABSENT en baseline; PARTIAL ahora | Cuarentena PostgreSQL con body/hash/topic/partition/offset; sin publicación de topic de errores |
| Reconciliación / rebuild / API consultas | PARTIAL | API de consulta por tenant y diagnóstico de cuarentena desplegada; reconciliación/rebuild ausentes. Véase admin-api.md |
| Health CLI | PRESENT_VALIDATED | emctl administra ESS, Docker y `/health/ready` |
| Observabilidad | PARTIAL | Logs y datasource health; faltan métricas de outbox, drift, duplicate/stale y salud explícita de consumer |
| Framework | PRESENT_VALIDATED | Java 21, Quarkus 3.38.0, Camel 4.21.0 resuelto y observado en pruebas |

La identidad inicial se conserva cuando existe lifecycle explícito; la clave primaria
no incluye tenant. Esta rama evita transferencia entre tenants, pero no permite
dos tenants con la misma clave funcional: ese cambio exige migración compatible.
Los estados SUCCESS son el contrato actual; SUCCEEDED, messageId y tenantId de
la arquitectura son objetivo, no aliases implícitos.

PostgreSQL commit → OpenSearch PUT → Kafka manual commit es una escritura dual.
Si OpenSearch falla, el estado/claim pueden existir y Kafka no se confirma; el
replay obtiene el estado más reciente sin incrementar versión. No hay outbox ni
reparación independiente. Mensajes fuera de orden pueden reemplazar el resultado
del proveedor; la prueba de duplicados no certifica protección contra stale.

Siguiente checkpoint: alinear la certificación compartida con OS_11 y después
avanzar en identidad tenant/eventKey, outbox ESS, reconciliación y API.
El PASS del laboratorio no equivale a certificación completa de V1.
Las solicitudes explícitas tienen protección STALE; los resultados de integración
tienen guardas terminales OS_11, no ordenamiento temporal genérico.

Hallazgo reproducido en runtime: offset 1348 de integration.results/0 bloqueaba
repetidamente por ausencia de resultId aunque Docker/HTTP estaban saludables.
La corrección clasifica únicamente rechazos permanentes de contrato/identidad;
conserva el mensaje en ess_quarantine y confirma tras el commit. Un fallo de DB
o proyección sigue sin confirmación. No se resetearon offsets. El contenido de
cuarentena puede contener datos del evento: no exportarlo a logs ni a Git.
