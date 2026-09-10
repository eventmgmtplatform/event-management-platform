# OS_09_01.IMP — End-to-End Lifecycle Orchestration — Fatal → Ticket → GNM → CACF → Clear

Nombre sugerido del chat: **OS_09_01.IMP — End-to-End Lifecycle Orchestration — Fatal → Ticket → GNM → CACF → Clear**.

El siguiente bloque es el prompt completo para iniciar la implementación en otro chat.
Esta especificación no implica que los cambios funcionales ya estén implementados.

---

Trabaja en `/opt/event-management-platform` para implementar **OS_09_01.IMP — End-to-End Lifecycle Orchestration**. El objetivo es que un evento fatal recorra automáticamente la cadena completa de ticket, alerta GNM, automatización CACF y clear, con estado durable, trazabilidad e idempotencia. Implementa y verifica el recorrido; no te limites a proponer arquitectura.

## Contexto y primera revisión

Ya se centralizó testing en `testing/`: suites Java/Node, fixtures, mocks, ambientes, certificaciones, catálogo de casos y runner. Lee primero:

- `testing/README.md`, `testing/CHANGELOG.md` y `testing/cases/catalog.json`.
- `testing/cases/UC-001-happy-path.md` y `testing/cases/UC-002-blackout.md`.
- `docs/event-processor/correlation-suppression-commands.md`, `docs/event-processor/rest-and-blackouts.md` y contratos de `services/event-processor/src/main/resources/contracts/`.
- `docs/cacf/contracts.md`, `docs/cacf/data-and-states.md` y `docs/cacf/architecture.md`.
- Documentación y código actuales de Event State Service, Integration Worker, ServiceNow y GNM.
- `CHANGELOG.md` y `docs/changelogs/README.md` para la política de registro por componente.

Inspecciona `git status` y las instrucciones aplicables. Hay cambios concurrentes y todavía sin commit: consérvalos. Verifica las capacidades reales del checkout; este prompt refleja un diagnóstico previo y no debe borrar avances posteriores. No renombres ramas existentes ni hagas reset, commit, push o despliegue productivo como consecuencia implícita de esta tarea. Si necesitas una rama nueva, usa el estándar `codex/` salvo instrucción explícita vigente distinta.

Diagnóstico de partida: el routing del Processor admite `SERVICENOW/CREATE_TICKET` por grupo, y el Worker tiene componentes GNM/CACF; falta completar o verificar el encadenamiento automático entre resultados, dependencias y cierres. `testing/run.py happy-path` devuelve BLOCKED. Revisa si algo de esto ya cambió antes de implementarlo.

## Happy path obligatorio

**Evento fatal → ticket ServiceNow confirmado → alerta GNM confirmada con ese ticket → solicitud CACF → ACK → respuesta de automatización exitosa → recuperación del monitoreo → clear del evento y cierres externos confirmados.**

1. Ingresar por Gateway un evento Zabbix fatal, `severity=5`, `Type=1`, con tenant/nodo/condición sintéticos. Recibir HTTP 202, procesar por Kafka y conservar eventId, eventKey y processingId.
2. Aplicar validación, enriquecimiento, blackout/supresión, correlación y política/routing. Sin restricciones aplicables, generar una única intención durable de creación de ticket para el ciclo correspondiente.
3. Ejecutar ServiceNow por el Worker. Confirmar la creación con ID del proveedor y número real devuelto; conservarlos y asociarlos al evento/grupo/ciclo.
4. A partir de ese resultado confirmado, emitir automáticamente el comando GNM. La alerta debe incluir **exactamente el número de ticket recibido**, además del contexto del evento. Confirmar OPEN y persistir incidentId. No usar números hardcodeados del fixture como sustituto del resultado.
5. Después de la confirmación GNM, solicitar automáticamente CACF con la misma identidad funcional y ticket. Persistir executionId, tramitar CREATE con NEXT, ACK y asociación del ticket/TKTUPDATE conforme al contrato existente. No exigir que un operador publique el comando.
6. El proveedor de prueba responde por el endpoint público de callback con `result / RESOLVE` (o REMEDIATION según contrato). CACF pasa a COMPLETED / REMEDIATED, publica su resultado y el estado se proyecta correctamente. Registrar la nota ITSM de éxito sin reasignación humana por ese éxito.
7. **REMEDIATED no equivale a clear**. Ingresar por Gateway la recuperación del mismo origen/nodo/condición (`Type=0`), nuevo eventId y el mismo eventKey. Reconocer el ciclo existente y actualizar el estado de recuperación, sin crear un ticket nuevo.
8. El clear debe disparar automáticamente el cierre de la alerta GNM y la transición correspondiente del ticket ServiceNow; confirmar las respuestas del proveedor y reflejar las transiciones en ESS. Determinar la política ITSM soportada por los contratos actuales: resuelto frente a cerrado. Documentarla y hacer explícito el estado esperado; nunca elegir un código numérico por intuición ni declarar CLOSED sólo por recibir HTTP 2xx.
9. Repetir callback y recuperación: no duplicar ticket, alerta, automatización ni efectos terminales; no revertir estados terminales por mensajes tardíos. Conservar trazabilidad y no dejar intenciones pendientes del caso al finalizar.

## Modificaciones requeridas

### Orquestación durable y responsabilidad por componente

- Identificar el punto existente adecuado para coordinar dependencias de negocio. Reusar Processor/Worker/ESS; justificar con una decisión de arquitectura cualquier componente adicional.
- Processor decide elegibilidad, correlación y política; Worker ejecuta proveedores; ESS conserva y proyecta estado. No convertir ESS en un segundo motor de routing ni escribir su estado directamente desde el harness.
- Implementar una máquina de estados o coordinación durable con transiciones explícitas. Persistir intención de siguiente paso y decisión atómicamente donde corresponda; publicar con outbox/replay seguro. No usar memoria de proceso, sleeps ni llamadas HTTP entrelazadas como garantía de continuidad.
- Encadenar resultados confirmados a comandos de GNM/CACF/cierre con condiciones previas verificables. Mantener estados de pendiente, reintentable, fallido y revisión; nunca convertir ausencia de confirmación en éxito.
- Conservar eventId de origen, eventKey, tenant, processingId, groupId/ciclo si aplica, commandId/resultId, ticket ID/número, incidentId y executionId. No confundir identidad del evento fuente con la identidad de comandos de grupo (`correlation:<groupId>`).
- En grupos con varios miembros, definir cuándo corresponde cerrar el ticket/alerta compartidos: el clear de un miembro no debe cerrar una situación que aún tenga miembros activos. La reapertura posterior debe respetar un ciclo nuevo sin duplicar el anterior.

### Routing, contratos y proveedores

- Extender el subconjunto admitido de reglas/adaptadores/envelopes para el recorrido, con validación tipada y compatibilidad de contratos existentes. No habilitar payload arbitrario ni acciones sin soporte real.
- Resolver el ticket desde el resultado durable de ServiceNow para GNM y CACF; resolver el incidente desde GNM para su cierre. Validar tenant, ciclo y dependencias antes de emitir cada comando.
- Reusar clasificación de errores, retries, ledgers, checkpoints y reconciliación ya presentes. Proteger efectos externos ante redelivery, reinicio, respuesta ambigua y publicación duplicada. Si el proveedor no ofrece idempotencia, reconciliar antes de repetir una mutación incierta; no prometer exactly-once externo.
- Conservar seguridad XML y semántica actual de CACF para UNKNOWN, ESCALATED, TIMEOUT, duplicados y callbacks tardíos. Añadir el camino REMEDIATED a la coordinación sin reinterpretarlo como recuperación del monitoreo.
- Resolver la política y contrato de actualización/cierre de ticket que falten, incluidas notas/códigos obligatorios y confirmación. No asumir que el cierre GNM implica cierre ITSM ni viceversa.

### Estado, persistencia y recuperación

- Añadir únicamente migraciones compatibles y necesarias; documentar orden, índices/unicidad, estrategia de actualización y reversión compatible. Conservar datos, offsets y auditoría existentes.
- Garantizar que reinicio/replay retomen desde el último estado confirmado. Evitar pérdida de intención entre commit y publicación, y duplicación entre ejecución remota y persistencia del resultado.
- Validar transiciones, mensajes fuera de orden, colisiones de identidad y aislamiento de tenants. Mantener consistencia de ESS/OpenSearch y posibilidad de distinguir recuperación del monitoreo de cierres externos pendientes.
- Definir explícitamente qué ocurre con clear temprano: antes del ticket, entre ticket y GNM o durante CACF. No lanzar una automatización obsoleta por un mensaje tardío ni cancelar ejecuciones del proveedor sin contrato soportado.

### Testing centralizado

- Implementar `python3 testing/run.py happy-path` como escenario real por las fronteras públicas. Sustituir el bloqueo únicamente cuando se implemente el recorrido y sus aserciones.
- El harness sólo configura reglas/mocks sintéticos, ingresa el fatal, envía el callback del proveedor y luego la recuperación. **No publicar manualmente comandos intermedios, fabricar integration.results ni modificar tablas para forzar avances.**
- Usar los servicios reales con PostgreSQL/Kafka y proveedores mock controlados; validar journals filtrados por las identidades del caso. No resetear journals ni datos compartidos.
- Preparar mappings GNM deterministas para apertura y cierre normales. Los mocks actuales contienen escenarios históricos de reconciliación que deben seleccionarse/aislarse; no depender accidentalmente de su prioridad.
- Exigir número de ticket e identificadores cruzados correctos, orden de pasos, unicidad de efectos, confirmaciones del proveedor, proyección ESS y limpieza de reglas propias en `finally`.
- Mantener y ejecutar UC-002: un blackout activo para el registro suprime ticket/GNM/CACF, conserva procesamiento/auditoría y respeta scope, tenant y límites temporales. Validar cómo se aplica a recuperaciones con integraciones ya abiertas; documentar la política sin cambiar silenciosamente el comportamiento existente.
- Agregar regresiones para ticket fallido, GNM fallido/timeout, CACF fallido/UNKNOWN/timeout, callback duplicado/tardío, clear duplicado/temprano, varios miembros, reapertura, proveedor caído y reinicio entre etapas. Distinguir pruebas de unidad, contrato, integración y E2E; indicar cuáles no se ejecutaron.
- IDs únicos, polling acotado, errores accionables, reportes ante fallos y códigos de salida coherentes. No dejar pruebas aprobadas por ausencia de routing o porque se omitieron todos los casos.

## Documentación y entrega

- Actualizar UC-001, catálogo, runbook y diagrama de secuencia con el comportamiento final y sus límites; mantener una lista concreta de dependencias no resueltas.
- Actualizar **el CHANGELOG de cada componente modificado**, sección Unreleased, con qué cambió, por qué, compatibilidad, migraciones y alcance. Usar el índice raíz; no duplicar historias divergentes.
- Guardar resultados, logs, snapshots y reportes **exclusivamente en `evidences/`**, ignorado por Git. Los changelogs y especificaciones documentan cambios; no contienen resultados de pruebas ni capturas. Conservar intacto el histórico `evidence/`.
- Registrar la evidencia de cada etapa con identidades y timestamps sintéticos; no exportar secretos o datos reales de clientes.
- Entregar código, pruebas reproducibles, documentación, changelogs y rutas de evidencias. Resumir capacidades implementadas, comandos ejecutados y limitaciones reales. No afirmar E2E aprobado si hubo comandos inyectados, confirmaciones ausentes o pasos omitidos.

## Definición de terminado

Una ejecución reproducible que comienza con **un solo fatal** genera automáticamente el ticket, la alerta GNM con ese ticket y la solicitud CACF; acepta su respuesta exitosa; y, después de ingresar la recuperación, completa clear y las transiciones externas definidas. Las identidades coinciden en toda la cadena; duplicados y reinicios no crean efectos extra; blackout sigue suprimiendo correctamente; documentación y changelogs reflejan lo implementado y las evidencias quedan fuera del código.
