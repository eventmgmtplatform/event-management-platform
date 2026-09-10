# Prompt para el chat de frontend — Blackouts v1 mínima

Implementa la administración manual de blackouts en la consola existente, conservando
su diseño, traducciones y navegación. El backend de escritura y el motor ya tienen
pruebas; tu alcance es conectar la interfaz y certificar el recorrido desde navegador.
No añadas seguridad, recurrencia, nuevos contratos de eventos ni escrituras directas
sobre PostgreSQL. Conserva los cambios de otros trabajos que encuentres en el workspace.

## Fuentes y objetivo

- Vista actual: `services/event-management-console/src/modules/product/ProductCatalogPage.tsx`.
- Rutas actuales: `services/event-management-console/src/app/router.tsx`.
- Proxy: `services/event-management-console/nginx.conf` (verifica el nombre vigente).
- API de escritura: `services/event-processor/src/main/java/com/eventmanagement/processor/adapters/http/AdminResource.java`.
- Contrato y límites: `docs/event-processor/rest-and-blackouts.md`.
- Schema: `services/event-processor/src/main/resources/contracts/blackout-v1.schema.json`.
- Aceptación backend: `testing/certifications/processor-blackout-write-certification.py` y `testing/e2e/blackout.py`.

La pantalla hoy sólo consulta `/api/catalog/views/blackouts`. Permitir crear, editar
mediante nueva versión, activar, desactivar, retirar, consultar historial y simular.
La fuente autoritativa de ejecución es el registro versionado del Processor.

## Conectividad que falta

Processor atiende `http://event-processor:8082/api/v1` dentro de Docker y
`http://127.0.0.1:8082/api/v1` en el host. El Nginx de la consola rechaza las rutas
`/api/` que no declara explícitamente; actualmente no publica esta API administrativa.
Agrega un proxy de mismo origen limitado a las rutas necesarias, por ejemplo
`/api/processor/v1/` → `http://event-processor:8082/api/v1/`, y usa esa base en el cliente.
Conservar status, ETag, If-Match, Idempotency-Key, Content-Type, X-Tenant-Id y X-Actor-Id.
No apuntar el navegador a `localhost:8082`: en un usuario remoto sería su propia máquina.
No ampliar CORS para resolver el proxy ni exponer rutas administrativas de mocks.

## Persistencia y tipos: evitar dos errores

`BLACKOUT` es el nombre de la capacidad, **no un valor válido de `rule.type`**.
Las definiciones persistidas usan `SCHEDULED` o `IMMEDIATE`. La lectura del catálogo
se corrigió para ambos tipos. No enviar `type: "BLACKOUT"`.

El catálogo mezcla `source: "Event Processor"` con registros anteriores de
`event_management.blackout`, marcados `source: "Catálogo PostgreSQL"`. Estos últimos
son sólo consulta: no presentarlos como reglas activas del motor ni editarlos usando
su UUID en la API del Processor. No crear una sincronización implícita entre tablas.
Los estados RETIRED deben mostrarse como retirados, no sólo como deshabilitados.

## API y headers

Todas las peticiones llevan `X-Tenant-Id`. Usar un tenant seleccionado explícitamente
para escribir; deshabilitar acciones en la vista agregada «Todos los tenants» hasta
seleccionar uno. `X-Actor-Id` puede identificar al operador local; no es identidad autenticada.

| Acción | Petición | Respuesta |
|---|---|---|
| Lista general | GET `/rules?limit=50&after=<cursor>` | `{items:[{id,latestVersion,activeVersion,status,revision}],next}` |
| Leer edición | GET `/rules/{id}` | Resumen + `rule` de la última versión + `checksum`; header ETag |
| Leer versión activa | GET `/rules/{id}?version=<activeVersion>` | Definición específica; ETag sigue siendo revisión actual del registro |
| Historial | GET `/rules/{id}/history?limit=50&after=0` | `{items:[{revision,version,status,actor,reason,createdAt,checksum}],next}` |
| Validar | POST `/rules/validate` con `{rule}` | `{valid:true,id,version,checksum}` |
| Crear/nueva versión | POST `/rules` con `{rule,reason}` | 201, `{ruleId,version,revision,status:"CREATED",requestId}` y ETag |
| Activar | POST `/rules/{id}/enable` con `{version,reason}` | 200, recibo y ETag |
| Desactivar | POST `/rules/{id}/disable` con `{version,reason}` | 200, recibo y ETag |
| Retirar | POST `/rules/{id}/retire` con `{version,reason}` | 200, recibo y ETag; acción terminal |
| Simular | POST `/simulations` con `{event,evaluatedAt,candidateRule?}` | Directiva, etapas, matches, checksum; sin cambios productivos |

La lista `/rules` mezcla capacidades y no contiene la definición: no asumir que todos
sus IDs sean blackouts. Usar el catálogo corregido para la tabla y GET al seleccionar
un registro del Processor. La lista del catálogo está acotada a 2000 registros; mostrar
su indicador `truncated`, no prometer resultados ilimitados ni paginación que no existe.

Mutaciones: `If-Match: "0"` al crear un ID nuevo; en otras acciones enviar el ETag
actual, incluidas comillas. Agregar `Idempotency-Key` único por acción lógica.
Reintentar la misma acción incierta con exactamente la misma clave, actor, cuerpo y
revisión; no generar otra clave hasta resolver si la operación original se confirmó.
No incrementar versiones/revisiones sólo de manera local; refrescar desde el servidor.

## Ejemplo válido para el formulario

Envolver esta definición en `{ "rule": ..., "reason": "Motivo del cambio" }`:

```json
{
  "id": "maintenance-router-1",
  "version": 1,
  "type": "SCHEDULED",
  "enabled": true,
  "scope": {"customerCode": "tenant-demo", "node": "router-1"},
  "schedule": {
    "timezone": "America/Mexico_City",
    "validFrom": "2026-09-10T18:00:00Z",
    "validTo": "2026-09-10T19:00:00Z"
  },
  "priority": 10,
  "reason": "Mantenimiento del recurso",
  "metadata": {"owner": "operations"}
}
```

El header tenant debe coincidir con scope.customerCode. `metadata.externalReference`
es opcional. El ID admite letras/números iniciales y luego letras/números/punto/guion/
guion bajo, máximo 128 caracteres. Motivo de cambio obligatorio, no vacío, máximo 2048.

## Estados y flujo de guardado

- POST crea una versión **sin activarla**, aunque `rule.enabled` sea true.
- `rule.enabled:true` hace a esa versión habilitable. El estado operativo está en
  `status` y `activeVersion`, no en ese booleano de la definición.
- Guardar y activar son dos operaciones. Si activación falla tras guardar, informar
  «guardado, pendiente de activación»; no aparentar rollback ni crear otra versión.
- Editar usa `version=latestVersion+1` y la revisión actual. Si v1 está activa y se
  guarda v2, v1 sigue ejecutándose hasta activar explícitamente v2.
- Mostrar por separado versión activa y última guardada. GET sin `version` devuelve
  la última; el catálogo prefiere la activa cuando existe.
- Desactivar/retirar deben indicar activeVersion si existe; de lo contrario latestVersion.
  Retirar es terminal y conserva historial; no hay DELETE físico ni reutilización del ID.
- No modificar IDs existentes ni cambiar su capacidad a POLICY/INVENTORY/etc.

## Ventana, alcance y simulación

SCHEDULED exige inicio y fin, con inicio < fin. IMMEDIATE exige inicio explícito y
puede omitir fin; «ahora» debe convertirse en un instante al confirmar, no recalcularse
al reintentar. Rango: inicio incluido, fin excluido. Zona IANA válida; enviar fechas
ISO 8601 con Z u offset. Resolver horas locales ambiguas/inexistentes antes de enviar.
No ofrecer RECURRING, tags, atributos arbitrarios ni selectores regex en este corte.

Scope admite igualdad exacta y conjunción de `customerCode`, `node`, `nodeAlias`,
`component`, `instanceId`, `monitoringSolution`. Omitir selectores opcionales vacíos.
Scope vacío afecta todo el tenant: mostrar ese alcance expresamente en la confirmación.
El motor evalúa el instante de procesamiento; una simulación puede fijar evaluatedAt.

Simulación mínima Gateway 1.1:

```json
{
  "event": {
    "schemaVersion": "1.1", "eventId": "simulation-unique-id", "eventKey": "router-1",
    "tenant": {"code": "tenant-demo"}, "resource": {"name": "router-1"},
    "summary": "Prueba de blackout", "lifecycleAction": "OPEN", "effectiveSeverity": 3,
    "timestamps": {"receivedAt": "2026-09-10T18:30:00Z"}
  },
  "evaluatedAt": "2026-09-10T18:30:00Z"
}
```

Sin candidateRule usa reglas activas. Con candidateRule reemplaza el snapshot por esa
candidata, incluso si no está habilitada: etiquetar como prueba de borrador, no como
resultado de todas las reglas vigentes. Recuperación usa `lifecycleAction:"CLOSE"`.
Mostrar Blackout.match y directive, nunca inferir «suprimido» sólo por enabled=true.

## Errores y pruebas de aceptación desde navegador

Mostrar errores 400/413/422 de formato/validación; 404 ausente; 409 conflicto de
revisión, idempotencia o transición; 428 precondición faltante; 503 indisponibilidad.
En 409 refrescar y permitir reconciliar, sin sobrescribir automáticamente. No mostrar
éxito ante error HTTP, ni sustituir datos por mocks silenciosos.

Ejecutar y guardar evidencia en `evidences/`:

1. Alta manual → registro persistido y visible, inicialmente no activo.
2. Activación → simulación de reglas activas coincide dentro de ventana/alcance.
3. Fuera de ventana, otro recurso y otro tenant no heredan esa supresión.
4. Guardar v2 mientras v1 activa → v1 sigue aplicando; activar v2 cambia el comportamiento.
5. Dos pestañas: la revisión obsoleta se rechaza y no sobrescribe la más reciente.
6. Doble clic/reintento no duplica versión ni cambio auditado.
7. Desactivación deja de aplicar; retiro conserva historial y bloquea reactivación.
8. Recargar navegador conserva lo registrado; caída del API muestra fallo explícito.
9. Los registros del catálogo anterior no ofrecen edición del motor.
10. Proxy de mismo origen conserva headers/ETag y códigos; ninguna escritura SQL directa.

Entregar rutas/archivos cambiados, pruebas y evidencia. Sólo declarar PASS de escritura
frontend después de estos casos en navegador. Backend PASS no certifica esta fase.
