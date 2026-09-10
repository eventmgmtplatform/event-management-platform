# Event State Service — baseline y certificación

Workstream `OS_05_ESS.IMP`, iniciado desde
`887baeaf51f1c9274786388dc36b175dce30b3bb` de
`feature/os-06-core-event-processor`, coincidente con su upstream al inicio.
Rama: `feature/os-05-core-event-state-service`. La rama conserva el estándar
`feature/os-*` del proyecto. Publicación pendiente; no hay promoción ni release.

## Alcance implementado

ESS consolida resultados del Worker por `eventKey`, conserva un ledger de
`resultId`, persiste en PostgreSQL y proyecta el estado en OpenSearch antes de
confirmar Kafka. No decide correlación, supresión, deduplicación funcional ni
ruteo: esas decisiones son del Processor.

Este checkpoint añade validación del contrato existente, bloqueo transaccional
para altas concurrentes, protección contra colisión de tenant, rollback para
excepciones SQL, cuarentena persistente de entradas inválidas y certificación repetible. No sustituye el diseño conceptual V1
por una afirmación de implementación completa. Véase [gaps](gaps.md).

## Ejecución repetible

```bash
bash scripts/emctl event-state-service test
# Java + servicio + reinicio en una sola ejecución:
python3 testing/certifications/event-state-certification.py --verify --restart
# Sólo servicio, con reinicio: reinicio de ESS y replay:
python3 testing/certifications/event-state-certification.py --restart
```

Antes de repetir, el runtime compartido debe incorporar lifecycle y las tablas
016/017. El runner comprueba ese requisito antes de crear eventos. Maven se
bloquea si un contenedor activo monta el target de ESS; usar el procedimiento
`lifecycle-prepare` descrito en `testing/README.md` para coordinar builds del laboratorio.

La acción CLI incluye Maven unitario e integración JTA; requiere el PostgreSQL
aislado descrito abajo. Para repetir sólo el flujo de servicio sin Maven, ejecutar
`python3 testing/certifications/event-state-certification.py`.

La plantilla compartida está en
`testing/services/event-state-service/resources/health/event-service.json`.
Contiene el resultado base, expectativas, checks obligatorios y objetivos V1
pendientes. Java y Python la consumen; no hace falta repetir el contexto en un
prompt. Cambiar o agregar una expectativa exige implementarla en el runner.
Un check requerido no ejecutado impide PASS, excepto el reinicio explícitamente
opcional, que se registra en `notRun`.

El escenario crea un tenant sintético único, dos reglas de correlación/ruteo y
un evento por HTTP. Comprueba gateway → Kafka → Processor → Worker → ServiceNow
mock → integration.results → ESS → PostgreSQL → OpenSearch. Conserva la
trazabilidad sourceEventId/commandId/resultId/eventKey. Después repite el resultado
y añade un resultado GNM sintético al mismo agregado: esto prueba consolidación
multiproveedor, no la ejecución del proveedor GNM. La variante `--restart`
reinicia exclusivamente ESS por la CLI y repite el resultado original. Un mensaje
inválido controlado verifica que se conserva en cuarentena sin cambiar el estado.

La ruta del gateway es `/api/v1/gateway`; Processor/Worker/ESS usan
`/health/ready`. El Worker puede tardar en estabilizar su consumidor PULL_RESTART.
El runner espera readiness y exige `SERVICENOW_BASE_URL` apuntando al mock local.
Las reglas se deshabilitan y retiran al terminar, incluso ante fallos. Se conservan
evento, ticket mock y auditoría bajo el tenant de la ejecución. No borra datos
compartidos. Los fallos de limpieza son FAIL y requieren atención operativa.

## Pruebas Java

```bash
cd services/event-state-service
mvn -o test
```

Para integración real se requiere un PostgreSQL **aislado** en loopback:15440,
base y usuario `ess_test`, contraseña de fixture `ess-test-only`:

```bash
docker run -d --name ess-cert-postgres -p 127.0.0.1:15440:5432 \
  -e POSTGRES_USER=ess_test -e POSTGRES_PASSWORD=ess-test-only \
  -e POSTGRES_DB=ess_test postgres:16
cd services/event-state-service
mvn -o verify -DskipITs=false -Dess.test.jdbc.url=jdbc:postgresql://127.0.0.1:15440/ess_test
```

El test crea una base aleatoria, aplica DDL 002/003/016/017, usa el repositorio mediante
CDI/JTA real y elimina únicamente esa base al terminar. No acepta otra URL.
No se declara PASS de integración si el servidor no está disponible.
La opción `-o` requiere dependencias Maven ya descargadas; omitirla en un entorno
nuevo. Los IT están excluidos por defecto para no exigir Docker en el build normal.

## Build, despliegue y evidencia

El script histórico `python3 scripts/event-state-deploy.py` construye la imagen Docker, conserva la
imagen anterior, recrea exclusivamente ESS y ejecuta la certificación con restart.
Respalda el esquema y aplica la migración aditiva `016-ess-quarantine.sql`.
Si falla, restaura la imagen previa; conserva la tabla aditiva y sus registros.
No cambia volúmenes ni restaura datos automáticamente. No usarlo para promover
OS_11: sólo aplica 016 y el runner actual requiere lifecycle. El despliegue
coordinado debe incluir las dependencias Processor/Worker y sus migraciones.
`POSTGRES_PASSWORD` es obligatorio; no hay contraseña predeterminada en el código.

`python3 scripts/event-state-discovery.py` captura esquema, restricciones, mapping,
aliases, salud y lag sin volcar eventos. La evidencia vive en
`evidence/os-05-ess/` para el historial y `evidences/os-05-ess/` para las nuevas
certificaciones, excluidas de Git. Los PKC y referencias originales viven
exclusivamente en esa evidencia local para entrega a library; no son código ni
se publican en Git. Los reportes JSON y SHA256SUMS permiten revisar una ejecución
sin releer logs completos.

## Incremento de ciclo de vida

Solicitudes explícitas OPEN/CLOSE/reapertura: [contrato](lifecycle-contract.md).
La adaptación se reanudó con plantilla 1.1.0 y checks obligatorios de lifecycle.
OS_11 cuenta con certificación de laboratorio; véase [estado](lifecycle-status.md).
Para repetir el flujo de negocio completo con proveedores simulados:

```bash
python3 testing/run.py happy-path
```

Este comando usa el laboratorio OS_11 ya preparado. No recompila ni promueve
el runtime compartido. El runner específico de ESS también inyecta resultados
para comprobar idempotencia/cuarentena, por lo que complementa UC-001.

## API administrativa

Consulta de estado e historial por tenant y diagnóstico de cuarentena: [contrato y CLI](admin-api.md).
