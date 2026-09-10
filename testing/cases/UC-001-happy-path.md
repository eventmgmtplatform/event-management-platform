# UC-001 — Fatal, ticket, GNM, CACF exitoso y clear

## Objetivo y alcance

Un evento fatal recorre automáticamente Gateway → Kafka → Processor → Worker →
ServiceNow/GNM/CACF → ESS. Los proveedores pueden ser mocks controlados, pero no
se permite crear manualmente comandos intermedios para declarar E2E aprobado.

## Precondiciones

Tenant/nodo/eventKey únicos; sin blackout/supresión; reglas de correlación y routing
activadas. Servicios saludables, topics y migraciones disponibles. Mocks seleccionados
para ticket creado, GNM abierto/cerrado y NEXT aceptado; callbacks correlacionados.
No resetear journals compartidos. Medir llamadas filtradas por las identidades del caso.

## Secuencia y aceptación

| Paso | Estímulo / observación | Criterio |
|---|---|---|
| 1 | Ingresar fixture fatal Zabbix (`severity=5`, `Type=1`) por Gateway | HTTP 202; eventId; evento OPEN normalizado; decision/processingId persistidos |
| 2 | Esperar integración de ticket | Un único CREATE_TICKET efectivo; ticket con ID y número confirmados por ServiceNow, asociado al evento/grupo |
| 3 | Esperar GNM | Una alerta OPEN confirmada; cuerpo del proveedor contiene **el mismo número de ticket**, no un fixture fijo; persistir incidentId |
| 4 | Esperar solicitud CACF | executionId único, evento/ticket correctos; CREATE aceptado por NEXT; ACK pasa a IN_PROGRESS; TKTUPDATE vincula ticket |
| 5 | Enviar callback NEXT `result / RESOLVE` | CACF COMPLETED / REMEDIATED; resultado publicado y proyectado; nota ITSM, sin reasignación humana por éxito |
| 6 | Ingresar recuperación (`Type=0`) del mismo origen/nodo/condición | Mismo eventKey, nuevo eventId, CLOSE/OK y ciclo correcto; evidencia y estado actualizados |
| 7 | Esperar cierres | GNM CLOSED confirmado por proveedor y ESS; ticket con transición ITSM de clear confirmada según política definida |
| 8 | Repetir callback/clear | Ningún nuevo ticket/alerta/automatización; ningún terminal revertido; outbox sin pendientes del caso |

`REMEDIATED` no significa clear: en el contrato CACF actual agrega una nota al ticket;
la recuperación del monitoreo dispara el clear y la resolución ITSM explícita descrita abajo.

## Implementación y entorno

`python3 testing/run.py happy-path` ejecuta el escenario público en
`testing/environments/lifecycle.compose.yml`. Preparar con
`python3 testing/run.py certification --name lifecycle-prepare`.
La regla opta por lifecycle con perfil tipado; las rutas anteriores conservan su alcance.
La política ITSM del laboratorio usa estado `resolved-test` y código
`monitor-recovered`, verificados por GET después del PATCH; ESS proyecta RESOLVED,
no CLOSED. Estos valores sintéticos no sustituyen el contrato de una instancia real.

El harness no emite comandos intermedios ni resultados ni cambia tablas de negocio.
Envía también ACK por el callback público. Las consultas SQL son exclusivamente de
lectura. El caso de reinicio comparte las mismas aserciones y reinicia servicios
entre NEXT SUBMITTED y ACK. Consultar
[runbook y dependencias pendientes](../../docs/event-processor/lifecycle-orchestration.md).

## Evidencias y limpieza

Guardar entradas, decisiones, commandId/resultId, ticket, incidentId, executionId,
transiciones ESS y llamadas de mocks del caso con timestamps y hashes, sólo en
`evidences/testing/<run>/happy-path`. Fallar ante IDs distintos, timeout, duplicación,
UNKNOWN, DLQ o confirmación ausente. Desactivar únicamente reglas propias y conservar
trazabilidad sintética; nunca borrar tablas/journals globales.

Cobertura de apoyo: UC-003 prueba CACF exitoso; suites GNM validan apertura/cierre;
certificación ESS verifica proyección. Ninguna por sí sola aprueba UC-001.
