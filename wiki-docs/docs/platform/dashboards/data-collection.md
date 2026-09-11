# Data Collection (06)

Ruta: `/dashboards/data-collection`, en el Nginx compartido del puerto 8091. API: `GET /api/dashboards/data-collection`. Admite PostgreSQL (predeterminado) y el contrato de API interna mediante `emctl ui oem-dashboards source set api --dashboard data-collection`.

## Modelo PostgreSQL y garantía de recepción

Migración aditiva e idempotente `infrastructure/postgres/init/025-gateway-data-collection.sql`:

- `event_management.gateway_receipt`: UUID de recepción, fecha del servidor, content type, cuerpo HTTP original en `bytea`, SHA-256 y longitud calculada. Un trigger impide UPDATE y DELETE. El rol del gateway sólo tiene INSERT y SELECT.
- `event_management.gateway_receipt_status`: observación mutable del procesamiento, event ID/key, cliente, fuente, severidad y código de error. El rol del gateway permite INSERT, SELECT y UPDATE.
- `dashboard_read.data_collection`: metadatos consultables, sin cargar cuerpos en las listas.
- `dashboard_read.data_collection_today` y `data_collection_history`: vistas complementarias sobre la misma tabla durable, separadas en la medianoche de `America/Mexico_City`. No se copian ni se borran filas al cambiar de día. No son dos tablas físicas.
- `dashboard_read.data_collection_original`: acceso al original por UUID bajo el rol lector existente.

El gateway guarda el cuerpo **antes de validar, normalizar o publicar en Kafka**, en una transacción que inserta original y estado RECEIVED con `synchronous_commit=on`. Si falla esa transacción responde 503 y no publica. El HTTP 202 sólo se emite tras persistir el original, confirmar la publicación Kafka y guardar PUBLISHED. La cabecera `X-Gateway-Receipt-Id` identifica la recepción.

Estados: RECEIVED, VALIDATED, PUBLISHED, REJECTED (HTTP 400), DELIVERY_FAILED y PUBLISH_UNCONFIRMED. Si falla el checkpoint tras el ACK Kafka se responde 503: el evento puede haberse publicado, por lo que un reintento puede duplicarlo. No existe aquí una transacción distribuida, replay automático ni promesa de exactly-once. Un cierre abrupto puede dejar la última observación en RECEIVED o VALIDATED. Los originales se conservan independientemente del estado posterior.

Se preservan los bytes del cuerpo entregado por HTTP a Camel, no las cabeceras de transporte ni el framing HTTP. Límite 1 MiB; rechazos de infraestructura anteriores a la ruta (por ejemplo 413) no ingresan al diario. La captura comienza al desplegar esta versión: no se reconstruyen cuerpos históricos a partir de eventos normalizados. No hay retención automática ni borrado en este módulo; su crecimiento requiere planificación de capacidad y respaldos PostgreSQL.

## Consulta e interfaz

Filtros `scope=today|history|all`, `from`, `to` (fechas inclusivas), `customer`, `source`, `status`, `q`, `receipt`, `page` y `limit` (máximo 100). Fechas usan America/Mexico_City. Los conteos corresponden al conjunto filtrado completo. Los UUID de recepciones rechazadas pueden no tener metadatos de cliente/fuente porque no superaron validación.

El detalle por UUID devuelve base64 sólo al solicitarlo y verifica SHA-256; la descarga conserva los bytes. La vista de texto usa UTF-8 y puede mostrar caracteres de sustitución para contenido binario: la descarga es la representación exacta. No se insertan eventos dummy en el diario: los registros de verificación son solicitudes reales al gateway identificadas como pruebas.

Credencial dedicada `gateway_collection`, miembro de `oem_gateway_collector`, en `.local/gateway-collection/runtime.env` (ignorado por Git, modo 0600). El montaje de ese directorio en el controlador permite conservar la credencial al iniciar servicios. Readiness del gateway: `/api/v1/gateway/ready` comprueba PostgreSQL. Las reglas de gateway de otro módulo conservan su configuración independiente.

Despliegue: `python3 scripts/dashboards/deploy-collection.py`. Validación PostgreSQL aislada: `testing/services/oem-dashboards/integration.py`. Validación HTTP y ciclo del mock de tickets: `scripts/dashboards/verify-collection.py`.
