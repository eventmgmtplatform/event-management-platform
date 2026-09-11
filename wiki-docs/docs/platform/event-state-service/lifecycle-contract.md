# ESS-02/03 — contrato explícito de ciclo de vida

> OS_11: el perfil optativo y la coordinación de resultados/cierres se documentan en
> [Lifecycle orchestration](../event-processor/lifecycle-orchestration.md). El alcance inicial descrito abajo permanece para rutas sin ese perfil.

El Processor publica `events.state.requested` desde su transacción de decisión:
registro de entrada, salida normalized, solicitud de estado, correlación y comandos
se confirman juntos. Los eventos rechazados a DLQ no generan solicitud de estado.
La outbox del Processor retiene el primer mensaje y su ID para replay.

ESS consume con grupo `event-state-service-lifecycle`, clave Kafka `eventKey`,
manual commit y lectura inicial earliest. Sólo confirma después de persistir
estado/historial y proyectar, o después de guardar un rechazo en cuarentena.
Los errores de infraestructura no se convierten en rechazos permanentes.

## Contrato 1.0.0

| Campo | Regla |
|---|---|
| schemaVersion | Literal 1.0.0 |
| messageId | Texto ≤128; ID determinista por tenant/eventId de origen |
| eventId | Texto ≤100; identidad del mensaje de origen, no sustituye la identidad inicial del agregado |
| eventKey | Texto ≤128, no vacío ni placeholder; conserva la clave del gateway |
| tenantId | Texto ≤100, obligatorio; colisión con otro tenant se rechaza |
| correlationId / causationId | Texto ≤128; processingId y eventId de origen respectivamente |
| transition | OPEN o CLOSE; REOPEN se deriva de CLOSED → OPEN |
| occurredAt | Timestamp con zona; actualmente receivedAt del gateway, precisión microsegundos |
| sourceSeverity / effectiveSeverity | Enteros 0–5; CLOSE requiere effectiveSeverity=0 |
| decisions | Objeto con directive, correlation y routing decididos por el Processor |
| event | Objeto con envelope original, conservado para auditoría |

El parser admite campos adicionales dentro de esta versión. No interpreta como
solicitudes de estado los mensajes de `events.normalized`. El campo `occurredAt`
representa la recepción del gateway en este contrato; no certifica orden por
reloj original del sistema monitorizado ni reemplaza una futura secuencia causal.

## Semántica

- Primer OPEN: OPEN, tally=1, versión=1.
- Otro OPEN con messageId nuevo y timestamp posterior: incrementa tally y versión.
- CLOSE posterior: CLOSED, effectiveSeverity=0, conserva sourceSeverity previa.
- OPEN posterior a CLOSED: REOPEN en historial, OPEN actual, tally+1.
- CLOSE sin apertura conocida: crea CLOSED, tally=0; un OPEN anterior será stale.
- Mismo messageId y JSON equivalente: DUPLICATE; no cambia versión, tally ni historial.
- Mismo messageId y contenido distinto: cuarentena, sin mutación del agregado.
- Timestamp menor o igual al último aplicado: STALE en ledger, sin cambiar estado
  ni insertar transición. Los empates se resuelven por el primero confirmado.
- eventId del agregado permanece estable. Un resultado de integración posterior
  no reabre ni reemplaza esa identidad si ya existe ciclo de vida explícito.

Tally cuenta solicitudes OPEN distintas aceptadas; no implementa deduplicación
funcional ni decide qué repeticiones debe emitir el Processor. Las decisiones de
supresión y correlación se conservan en state_payload; no se vuelven a calcular.

## Persistencia y proyección

Migración aditiva 017: columnas de severidad/tally/último estado/payload,
`ess_state_request` para idempotencia y stale, `ess_event_transition` para historial.
Una transacción JTA serializa el agregado con el mismo advisory lock que los
resultados de integración y confirma las tres escrituras juntas.

La proyección vuelve a leer el estado confirmado bajo el mismo lock antes de
escribir OpenSearch, evitando que un caller retrasado sobrescriba una versión
más nueva producida por estos dos consumidores. El lock se mantiene durante HTTP;
esto limita el throughput de una misma clave. Sigue siendo escritura dual: si
OpenSearch falla, Kafka reintenta y el ledger evita duplicar el efecto de negocio.
Outbox ESS, publicación events.lifecycle, reconciliación y protección contra
escritores externos siguen pendientes. PostgreSQL es la autoridad.

La PK heredada continúa siendo event_key global. Esta entrega rechaza cruces de
tenant; no promete permitir la misma clave en dos tenants sin migración adicional.
Las claves `correlation:...` de situaciones y sus tickets son agregados distintos
de los eventos de origen: un CLOSE del origen no cierra automáticamente el ticket
ni la situación. ACKNOWLEDGED, SUPPRESSED y EXPIRED requieren contrato posterior.

## Despliegue y aceptación

`python3 scripts/event-state-lifecycle-deploy.py` construye ambos servicios,
respalda el esquema, aplica 016/017, crea el topic si falta, inicia ESS antes del
Processor y ejecuta la certificación con reinicio. Ante fallo restaura las imágenes
anteriores; conserva schema aditivo y registros. No resetea offsets ni volúmenes.

La plantilla integral verifica ambos agregados por separado y agrega
lifecycle-open-close-reopen y lifecycle-replay-stale, más replay de ambos consumers
tras reinicio. La prueba de integración JTA comprueba rollback del historial,
concurrencia, severidad, tally, colisión de tenant y coexistencia con resultados.
