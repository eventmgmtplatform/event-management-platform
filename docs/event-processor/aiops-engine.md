# AIOps Engine — módulo mínimo independiente

El módulo es operable dentro de Event Processor mediante REST y un puerto HTTP a un
mock WireMock, igual que los proveedores simulados locales. Su modelo administrativo
es independiente de POLICY/BLACKOUT/CORRELATION. No agrega etapas al pipeline, campos
a los contratos de eventos, reglas ni comandos, y no ejecuta acciones de remediación.

La conexión futura deseada es Kyndryl Bridge. **El protocolo interno del mock no es
el contrato de Kyndryl Bridge y no certifica compatibilidad con ese proveedor.**
`AiopsProvider` permite reemplazar el adaptador cuando se disponga de su API real.
La respuesta actual es sintética y constante; no representa inferencia ni inteligencia real.

## REST

Base: `http://localhost:8082/api/v1/aiops`. Todas las operaciones requieren
`X-Tenant-Id`; `X-Actor-Id` es opcional. Son metadatos declarados, sin autenticación.

| Método | Ruta | Operación |
|---|---|---|
| POST | `/api/v1/aiops` | Crear configuración, `If-Match: "0"` |
| GET | `/api/v1/aiops?limit=50&after=` | Listar activas y deshabilitadas, sin eliminadas; máximo 100 por página |
| GET | `/api/v1/aiops/{id}` | Leer configuración y ETag |
| PUT | `/api/v1/aiops/{id}` | Reemplazar nombre/estado con `If-Match` del ETag actual |
| DELETE | `/api/v1/aiops/{id}` | Baja lógica con `If-Match` actual |
| POST | `/api/v1/aiops/{id}/assessments` | Consultar proveedor usando una configuración habilitada |

Creación:

```json
{"id":"bridge-local","name":"Bridge prototype","enabled":false}
```

Actualización: `{"name":"Bridge prototype","enabled":true}`. Respuesta de configuración:
`{"id":"bridge-local","name":"Bridge prototype","enabled":true,"revision":2}`.
Consulta:

```json
{"resource":"node-demo","summary":"Synthetic signal","severity":3}
```

Respuesta sintética:

```json
{"configurationId":"bridge-local","revision":2,"provider":"INTERNAL_MOCK","assessment":{"recommendation":"INVESTIGATE","confidence":0.75}}
```

POST crea con 201; lectura/actualización/consulta responden 200; DELETE responde 204.
Configuración ausente: 404; revisión obsoleta, ID existente o configuración deshabilitada:
409; falta If-Match: 428; proveedor inaccesible, lento o inválido: 503 sanitizado.
Validación estructural/tipos/tamaños rechaza entradas inválidas con 400/413/422.
El namespace de IDs es propio de AIOps y por tenant. La baja conserva el ID y evita
recreaciones accidentales. No hay recibos de Idempotency-Key: después de una respuesta
incierta, consultar GET y su revisión antes de reintentar una mutación.

## Persistencia y topología

Migración `015-processor-aiops.sql`: configuración actual y auditoría inmutable
`aiops_change` (actor, operación, revisión, nombre/estado, fecha). Cada mutación y su
registro de auditoría se confirman juntos. El borrado es lógico. Las evaluaciones
son consultas sin persistencia de señales ni resultados; no escriben al outbox.
La revisión devuelta identifica la configuración leída al comenzar la consulta;
una deshabilitación concurrente no cancela llamadas que ya estaban en curso.

`aiops-mock` usa WireMock 3.13.1, puerto local 8183, health propio. Processor consume
`AIOPS_MOCK_URL=http://aiops-mock:8080`, ruta interna `/internal/aiops/assessments`.
No hay endpoint configurable por solicitudes REST. Timeout de conexión 2 s y solicitud
3 s, sin reintentos ni redirecciones. La indisponibilidad del mock no detiene el
procesamiento normal de eventos. La configuración persiste entre reinicios del Processor.

## Defect Prevention

DP-EP-09: API real de Bridge, autenticación del proveedor, mapeo de solicitudes/respuestas,
reintentos y política de errores reales, historial de consultas, integración opcional
al pipeline y cualquier cambio de contratos quedan pendientes. No habilitar llamadas
reales cambiando únicamente la URL: se requiere implementar y certificar el adaptador.
