# CACF Core Foundation — implementación local

## Índice de documentación de código

- [Arquitectura y transacciones](architecture.md).
- [Referencia de clases, métodos y dependencias](code-reference.md).
- [Contratos REST, Kafka y XML](contracts.md).
- [Modelo de datos, estados y garantías](data-and-states.md).
- [Runbook operativo y verificación del PKC](operational-runbook.md).
- [Decisiones de diseño](decisions-adr.md).
- [Validación ejecutada](validation.md).
- [Defect Prevention](defect-prevention.md).
- [Arquitectura editable Draw.io](CACF-local-architecture.drawio).

La documentación técnica se versiona junto al código. El PKC y sus materiales
de library se conservan fuera de Git, conforme a la política global del proyecto.

## Alcance autorizado

CACF y las dependencias estrictamente necesarias para ejecutarlo localmente con
Docker Compose. No incluye Cloud Build, Terraform, Kubernetes ni mejoras generales
de ServiceNow/GNM que no bloqueen CACF.

- Rama: `feature/os-05-cacf-core-foundation`.
- Base: `006543a7932d5e05d4694a280ed1eec23f8786d5`.
- Ubicación: `services/integration-worker`, paquete `com.eventmanagement.integration.cacf`.
- Las acciones ServiceNow deben utilizar la foundation existente.
- Las observaciones fuera del alcance se registran en `defect-prevention.md`.
- Resultado de las pruebas locales: [evidencia de validación](validation.md).

## Contrato conocido

- NEXT CREATE: `POST /tupix/api/v1/netcool/tickets`, `text/xml`, HTTP Basic.
- NEXT TKTUPDATE: `POST /tupix/api/v1/netcool/incidents`.
- Callback compatible: `POST /data`.
- RequesterID: `sourceSystem:sourceSerial:customerCode`.
- ProviderID: identificador generado por NEXT.
- `itsmTicketNumber` se mapea únicamente en el adaptador NEXT a FlexField `mappedTo="ipc"`.
- Timeout HTTP configurable: 180 segundos por defecto.
- Timeout de resultado persistente configurable: 10 minutos por defecto.
- Respuesta válida desconocida: `outcome=UNKNOWN`, `requiresReview=true`, evidencia
  original persistida y ninguna acción destructiva automática.

## Especificaciones recuperadas

Fuente: tarea `OS_05_CACF.IMP — CACF Core Foundation — Contract Discovery & Runtime Implementation`,
id `6a9f90c3-729c-83e8-9669-4e0b6a15996f`, decisiones CACF-02.1, CACF-03 y CACF-04.
La revisión se recuperó mediante lectura de la tarea, sin modificarla.

Se conserva la estructura ServiceIncident y el namespace
`http://b2b.ibm.com/schema/IS_B2B_CDM/R2_2`. Los fixtures locales son sintéticos,
derivados del diseño; no se presentan como los archivos históricos originales.
Los campos opcionales no proporcionados se envían vacíos; la descripción utiliza
los labels del contrato recuperado. El catálogo desconocido usa UNKNOWN seguro.

## Implementación local

- API `POST /api/v1/automations` con `Idempotency-Key=executionId`.
- Consulta `GET /api/v1/automations/{executionId}`.
- Asociación posterior de ticket: `PUT /api/v1/automations/{executionId}/ticket`,
  cuerpo `{"number":"INC..."}`. La asociación no puede cambiar a otro ticket.
- Callbacks `POST /data` y `POST /api/v1/providers/next/callback`, mismo servicio.
- Token local externo en header `X-CACF-Token` para las APIs CACF.
- Kafka: `integration.commands`, proveedor `CACF`, operación `AUTOMATION_REQUESTED`,
  payload igual al request REST. `eventId` y `tenant` del envelope deben coincidir
  con el payload. Grupo independiente `cacf-admission`, confirmación manual tras
  persistencia. El consumidor anterior ignora CACF.
- Resultados compatibles en `integration.results`; comandos inválidos CACF envían
  identidad y error a `events.dlq`, sin copiar el payload a logs/DLQ.
- No se crean tópicos físicos adicionales.
- Migración `008-cacf-core.sql`: ejecuciones, evidencia, resultados, outbox y despacho.
- Resultado y outbox comparten una transacción; publicación al menos una vez con
  identificadores estables. La intención permanece pendiente si Kafka falla.
- ACK asíncrono establece el deadline; EWT se conserva como dato informativo.
- Duplicados y callbacks tardíos se conservan sin repetir el resultado terminal.
- `raw_payload` guarda los bytes originales y `payload_sha256` su huella.
- XML rechaza DTD, entidades externas, XInclude y profundidad superior a 64.
- CREATE y TKTUPDATE no tienen reintentos ciegos. Una llamada incierta se conserva
  para revisión; el sistema no promete exactly-once distribuido.
- Un plazo separado para ausencia de ACK evita ejecuciones SUBMITTED eternas:
  `NEXT_HTTP_TIMEOUT_SECONDS + CACF_ACK_TIMEOUT_SECONDS` desde el envío.
- ServiceNow conserva la propiedad del HTTP de tickets. Se amplió su cliente con
  lookup por número y PATCH, operación interna `APPLY_AUTOMATION_RESULT`.
- El outbox genera asignación al holding group y luego notas/reasignación humana
  según resultado. UNKNOWN no genera acción terminal. REMEDIATED agrega nota;
  no cierra automáticamente sin una política de resolución certificada.
- Una acción ServiceNow con entrega incierta no se repite automáticamente.
- Métricas acotadas en `GET /metrics` (token CACF); salud en `/health/ready`.

## Configuración

| Variable | Default / regla |
|---|---|
| CACF_ENABLED | false; activación explícita |
| NEXT_BASE_URL | http://next-mock:8080 |
| NEXT_USERNAME / NEXT_PASSWORD | externos; obligatorios al activar |
| CACF_API_TOKEN | externo; obligatorio al activar |
| NEXT_HTTP_TIMEOUT_SECONDS | 180 |
| CACF_RESULT_TIMEOUT_SECONDS | 600 |
| CACF_ACK_TIMEOUT_SECONDS | 600 |
| CACF_XML_MAX_BYTES | 1048576 |

La contraseña NEXT y el token no forman parte de payloads, resultados ni logs.
Las credenciales fijas de `testing/environments/cacf.compose.yml` son exclusivamente fixtures
públicos del laboratorio aislado, no credenciales reales.

## Entorno aislado de certificación

Desde la raíz:

```sh
docker compose -p cacf-certification -f testing/environments/cacf.compose.yml up -d postgres next-mock servicenow-mock kafka kafka-init
```

Antes de las pruebas JDBC, el worker de ese proyecto debe estar detenido para que
sus publicadores no compitan con las transacciones controladas de los tests:

```sh
docker compose -p cacf-certification -f testing/environments/cacf.compose.yml stop integration-worker
cd services/integration-worker
mvn -o test -Dcacf.test.jdbc.url=jdbc:postgresql://localhost:15439/cacf_test
```

Luego, desde la raíz:

```sh
docker compose -p cacf-certification -f testing/environments/cacf.compose.yml up -d --build integration-worker
python3 testing/certifications/cacf-local-certification.py
```

La prueba E2E reinicia únicamente el worker del proyecto aislado. Incluye
CREATE/ACK/TKTUPDATE, duplicados, timeout, callback tardío, UNKNOWN, ingreso Kafka,
ticket posterior al ProviderID y la reasignación mediante ServiceNow mock.

Puertos locales: worker 18083, NEXT mock 18183, ServiceNow mock 18184, PostgreSQL
15439. No utiliza la base ni el broker del runtime existente.

## Overlay para el runtime local existente

`infrastructure/docker-compose.cacf.yml` se combina con el Compose principal.
Primero se suministran las variables externas requeridas y se ejecuta únicamente
la migración CACF; después se reconstruye el worker. Ejemplo desde la raíz:

```sh
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.cacf.yml run --rm --no-deps cacf-migrate
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.cacf.yml up -d --build integration-worker next-mock
```

No se aplica automáticamente a los servicios existentes durante la certificación.
No se incluyen Cloud Build ni Terraform.

## Límites explícitos de la liberación local

No certifica NEXT ni ServiceNow productivos. Falta validar contra sus credenciales
y contratos operativos reales. No porta CLEAR, failover legacy, IPCenter ni DB2.
TKTUPDATE_CLOSE y cierre ITSM automático permanecen fuera hasta confirmar su
necesidad y política. La evidencia desconocida queda persistida para revisión.

[Historial de cambios del componente](CHANGELOG.md).
