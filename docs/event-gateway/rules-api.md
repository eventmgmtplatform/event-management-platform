# API de reglas de event-gateway

La administración y ejecución viven en el mismo servicio y puerto 8081. No dependen
del catálogo de event-processor. Este catálogo aplica reglas de admisión, adaptación
de campos, validación adicional y enriquecimiento base antes de publicar en `events.raw`.

## Endpoints

Todos requieren `X-Gateway-Admin-Key`. Sin clave configurada responden 503;
con clave ausente o incorrecta, 401. La clave es administrativa global, no una
identidad de cliente ni autenticación del endpoint de ingreso.

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/v1/gateway/rules` | Catálogo, incluyendo reglas desactivadas |
| POST | `/api/v1/gateway/rules` | Crea una regla; 201 y ETag de revisión |
| GET | `/api/v1/gateway/rules/{id}` | Regla y ETag; 404 si no existe |
| PUT | `/api/v1/gateway/rules/{id}` | Reemplaza la definición completa; exige `If-Match` |
| GET | `/api/v1/gateway/rules/{id}/history` | Últimas 100 versiones, de más reciente a más antigua |
| POST | `/api/v1/gateway/rules/validate` | Valida una definición sin guardarla |
| POST | `/api/v1/gateway/rules/simulate` | Ejecuta el flujo completo sin guardar recibos ni publicar en Kafka |

Para desactivar una regla se usa PUT con `enabled:false`. No hay eliminación física:
se conserva su historial. POST sobre un ID existente y PUT con revisión obsoleta
responden 409. PUT sin `If-Match` responde 428. La definición inválida devuelve 400;
la indisponibilidad del catálogo devuelve 503 sin detalles internos de base de datos.

## Definición

Campos comunes obligatorios: `id`, `stage`, `priority`, `enabled`, `match`.
El ID admite letras, números, guion y guion bajo, hasta 64 caracteres.
`priority` es un entero 0–10000; menor valor se ejecuta primero, con desempate por ID.
El catálogo admite como máximo 256 reglas, incluidas las desactivadas.

`match` es un objeto de igualdades exactas sobre campos del evento de entrada:
todas deben cumplirse; `{}` aplica a todos los eventos. Los tipos importan:
`"5"` y `5` son valores diferentes. No se admiten rutas anidadas, scripts o consultas remotas.

| Etapa | Operaciones | Momento |
|---|---|---|
| `INGESTION` | `reject:true` | Rechaza eventos que coincidan, después de guardar el original |
| `NORMALIZATION` | `copy`, `set` | Adapta campos antes del normalizador 1.0/1.1 |
| `VALIDATION` | `required` | Exige campos escalares presentes y no vacíos después de los mapeos |
| `ENRICHMENT` | `copy`, `set` | Produce `enrichment.base` después de normalizar el contrato |

`copy` usa `{ "destino": "campoOrigen" }`. Un origen ausente o no escalar rechaza
el evento. `set` asigna constantes escalares no nulas. Ambos admiten hasta 64 campos;
`required` admite hasta 64 nombres. Los nombres deben comenzar con letra y contener
únicamente letras, números o guion bajo, hasta 64 caracteres. Dentro de una regla,
`copy` lee una copia previa y `set` prevalece. Entre reglas, la última asignación gana.
Las condiciones de reglas posteriores ven los cambios de normalización anteriores.
El enriquecimiento lee el evento adaptado, pero no modifica ese evento ni alimenta
las condiciones de otras reglas de enriquecimiento.

La validación fija de los contratos 1.0/1.1 siempre se ejecuta. Las reglas adicionales
no pueden omitirla. El enriquecimiento no sobrescribe el ID, la clave Kafka o el
lifecycle: sus campos quedan exclusivamente dentro de `enrichment.base`.

Se conserva `originalEvent` tal como entró y el recibo durable conserva los bytes.
La salida añade `gatewayRules.applied` y `gatewayRules.revisions` para identificar
las reglas ejecutadas y las versiones del catálogo utilizado. Se lee un único
snapshot por evento; el cambio de reglas afecta solicitudes posteriores.

## Ejemplo: enriquecimiento base

Definición para POST, PUT o `/rules/validate`:

```json
{
  "id": "sdc-base",
  "stage": "ENRICHMENT",
  "priority": 10,
  "enabled": true,
  "match": { "CustomerCode": "SDC" },
  "set": { "environment": "production", "location": "MX" },
  "copy": { "customerCode": "CustomerCode" }
}
```

Otras acciones: normalización `"copy":{"resource":"hostname"}`;
validación `"required":["CustomerCode"]`; admisión `"reject":true` con
`"match":{"source":"blocked-source"}`.

Ejemplo de edición: enviar la definición completa con `enabled:false` a
`PUT /api/v1/gateway/rules/sdc-base`, incluyendo `If-Match: "1"` si esa es la revisión
obtenida por GET. La respuesta lleva `ETag: "2"`.

## Simulación

```json
{
  "event": { "resource": "host01", "summary": "CPU alta", "severity": 5, "status": "PROBLEM" },
  "rules": [
    { "id": "base", "stage": "ENRICHMENT", "priority": 10, "enabled": true,
      "match": {}, "set": { "location": "MX" } }
  ]
}
```

`rules` es opcional: si falta se usa el catálogo persistido; si se envía reemplaza
el conjunto completo para esa simulación. `rules:[]` prueba solamente el contrato
fijo. Las candidatas tienen revisión 0 y no se guardan. La salida incluye
`mode:SIMULATION` y `event` con el evento final. UUID y timestamps de simulación
son nuevos en cada llamada. Las reglas que rechazan producen HTTP 400.

## Configuración y despliegue

1. Aplicar `infrastructure/postgres/init/026-gateway-rules.sql` después de la 025
   sobre la base del gateway. La migración es repetible y concede permisos al rol
   `oem_gateway_collector`; el usuario JDBC debe tener ese rol.
2. Configurar `GATEWAY_RULES_ADMIN_KEY` mediante secretos del entorno.
3. Configurar `GATEWAY_RULES_ENABLED=true` y desplegar la nueva imagen.

En el despliegue local, Compose carga ambas variables desde
`.local/gateway-rules/runtime.env`, excluido de Git y con permisos 0600. El script
`python3 scripts/gateway-rules-deploy.py` construye la imagen, aplica la migración,
conserva o genera la clave y activa únicamente el gateway. Guarda la imagen anterior
y el resultado de verificación en esa misma carpeta privada; si falla la activación,
restaura la imagen y configuración previas. La migración aditiva permanece aplicada.
No imprime credenciales ni modifica el catálogo existente.

Sin configuración local, el valor predeterminado de ejecución es `false`
para permitir aplicar la migración antes de activar el nuevo flujo. La administración
es independiente de ese interruptor y está cerrada si no se configura una clave.
Con ejecución habilitada, un fallo de lectura del catálogo impide publicar el evento;
no se ejecuta silenciosamente sin reglas. Sin reglas activas se mantiene el contrato
base y se añade la trazabilidad del catálogo.

La migración no se aplica automáticamente a volúmenes existentes al iniciar Compose;
la aplica el script de despliegue o el operador mediante el paso 1.

## Pruebas

Desde `services/event-gateway`: `mvn -B -ntp test -DargLine=`.
Incluye normalizador, ejecución por etapas, preservación del original, autorización,
simulación y control de revisión. Los reportes van a
`evidences/testing/maven/event-gateway/surefire`.

La prueba PostgreSQL se habilita con `GATEWAY_RULE_TEST_JDBC_URL` hacia una instancia
desechable y aislada que permita el usuario `postgres`. Crea schema/rol y aplica la
migración dos veces; verifica persistencia, historial, dos ediciones concurrentes
y rollback si falla la escritura del historial. Nunca apuntarla a la base operativa.

Después de `mvn package`, ejecutar desde la raíz
`python3 testing/certifications/gateway-rules-http.py` con la misma variable JDBC.
Levanta únicamente las rutas administrativas en `127.0.0.1:58091`, comprueba
autorización, creación, consulta, edición, conflictos, historial y simulación,
y detiene el proceso al terminar. El puerto puede cambiarse con
`GATEWAY_RULE_TEST_HTTP_PORT`. No inicia rutas de recibos ni Kafka.

Los resultados de ejecución se conservan localmente en `evidences/`, fuera de Git.

## Activación local

La API fue activada localmente el 2026-09-10 con la migración 026 y la ejecución
de reglas habilitada. La activación conserva el catálogo y recrea sólo el gateway.
Para consultar el estado actual, usar `/api/v1/gateway/ready` y la consulta
autenticada `/api/v1/gateway/rules`. La credencial permanece exclusivamente en
el archivo privado del entorno; no forma parte de la documentación ni del commit.

## Límites

No incorpora RBAC por usuario/tenant, aprobación de cambios, activación atómica de
varias reglas, búsquedas de inventario, correlación ni reglas con código ejecutable.
El historial registra definición, revisión y fecha, sin atribución individual:
la autenticación actual es una clave administrativa compartida.
