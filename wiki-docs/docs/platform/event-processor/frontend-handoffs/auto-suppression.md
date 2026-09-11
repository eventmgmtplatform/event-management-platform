# Prompt para el chat de frontend — Auto-suppression v1 mínima

Implementa la administración manual de SUPPRESSION en la consola existente después
del cierre backend. Reutiliza componentes y patrones de Blackouts, sin modificar
contratos de eventos, seguridad ni el motor. Conserva los cambios de otros trabajos.
No declarar certificación de navegador a partir de la prueba backend.

## Alcance y diferencia con blackout

La supresión se decide automáticamente al procesar cada evento, pero las entradas
son registros locales administrados por el operador. No existe aún sincronización
con una API de cambios/mantenimiento ni validación externa de aprobaciones.

`source`: MANUAL, MAINTENANCE o CHANGE.
`externalStatus`: ACTIVE, APPROVED, CANCELLED o COMPLETED.
Sólo ACTIVE/APPROVED son elegibles, siempre que coincidan tenant, scope y ventana.
CANCELLED/COMPLETED no suprimen aunque la regla esté habilitada. El estado declarado
no debe presentarse como una aprobación verificada por un proveedor.

## Lectura y fuente de verdad

La pantalla actual `/auto-suppression` es de consulta mediante
`GET /api/catalog/views/auto-suppression`. Usa el registro versionado del Processor,
tipo SUPPRESSION. Respuesta `{items,truncated,mode:"read_only"}`; máximo 2000 registros.
Filas: id, tenant, status, enabled, active_version, latest_version, revision,
configuration y source. `row.source="Event Processor"` identifica el catálogo;
`row.configuration.source="CHANGE"` identifica el origen declarado de mantenimiento.

Mostrar por separado estado administrativo (ENABLED/DISABLED/RETIRED), versión activa,
última versión y externalStatus. No reducirlos a un único toggle. La lectura del
catálogo prefiere la versión activa, mientras GET `/rules/{id}` devuelve la última.

## Escritura por API

Reutilizar el proxy de mismo origen `/api/processor/v1/` del módulo Blackouts hacia
`http://event-processor:8082/api/v1`. No usar localhost del navegador ni SQL directo.

| Acción | API |
|---|---|
| Consultar última versión | GET `/rules/{id}`; ETag y resumen con rule/checksum |
| Consultar versión específica | GET `/rules/{id}?version=N` |
| Historial | GET `/rules/{id}/history?limit=50&after=0` |
| Validar | POST `/rules/validate` con `{rule}` |
| Guardar nueva versión | POST `/rules` con `{rule,reason}`; 201 y recibo `{ruleId,version,revision,status,requestId}` |
| Activar/desactivar/retirar | POST `/rules/{id}/enable`, `/disable`, `/retire` con `{version,reason}` |
| Simular | POST `/simulations` con `{event,evaluatedAt,candidateRule?}` |

X-Tenant-Id obligatorio, X-Actor-Id metadata local. Seleccionar tenant antes de escribir.
Mutaciones requieren If-Match con revisión entre comillas ("0" para ID nuevo) y una
Idempotency-Key por acción lógica. Reintentos inciertos mantienen exactamente clave,
actor, cuerpo y revisión; no permitir otra escritura hasta resolver el resultado.

## Definición válida

```json
{
  "id":"change-router-1", "version":1, "type":"SUPPRESSION", "enabled":true,
  "priority":10, "source":"CHANGE", "externalStatus":"APPROVED",
  "scope":{"customerCode":"tenant-demo","node":"router-1"},
  "schedule":{"timezone":"America/Mexico_City",
              "validFrom":"2026-09-10T18:00:00Z","validTo":"2026-09-10T19:00:00Z"},
  "reason":"Mantenimiento del recurso",
  "metadata":{"owner":"operations","externalReference":"change-123"}
}
```

La definición se envuelve en `{rule,reason}`; el reason exterior es motivo del cambio
administrativo. IDs comparten namespace con las otras capacidades y no son editables.
El ID admite letras/números al inicio y luego letras/números/punto/guion/guion bajo,
máximo 128 caracteres. Motivo administrativo obligatorio, máximo 2048 caracteres.

Scope admite igualdad exacta y conjunción sobre customerCode, node, nodeAlias,
component, instanceId, monitoringSolution. Omitir opcionales vacíos; customerCode
explícito debe coincidir con el tenant. Scope vacío afecta todo el tenant: mostrarlo
expresamente. No ofrecer tags, atributos libres, regex ni recurrencia.

Exigir inicio y fin explícitos, inicio < fin, zona IANA, fechas ISO 8601 con offset o Z.
Inicio incluido, fin excluido. A diferencia del blackout IMMEDIATE, SUPPRESSION no
admite fin abierto. No aceptar horas locales ambiguas/inexistentes sin resolver su offset.

## Flujo de versiones

Guardar no activa, incluso con enabled:true. Esa propiedad permite habilitar la versión;
la ejecución depende de status/activeVersion y externalStatus/ventana/scope.
Editar crea latestVersion+1 con revisión actual; la versión anterior sigue activa
hasta activar explícitamente la nueva. Esto también aplica al cambiar a CANCELLED:
guardar una cancelación no detiene una versión APPROVED activa hasta activarla.
Si se requiere detener inmediatamente, usar disable sobre la versión activa.

Guardar y activar son dos operaciones: informar éxito parcial si sólo se guardó.
Desactivar/retirar indica activeVersion si existe, si no latestVersion. Retiro es
terminal, conserva historial y no permite reutilizar ID. No hay DELETE físico.
Conflictos 409 requieren refrescar/reconciliar, no sobrescribir automáticamente.
Mostrar errores 400/413/422, 404, 428 y 503; no sustituir datos por mocks.

## Simulación y evidencia

Evento Gateway 1.1: schemaVersion, eventId único, eventKey, tenant:{code}, resource:{name},
summary, lifecycleAction OPEN o CLOSE, effectiveSeverity y timestamps:{receivedAt}.
Sin candidateRule usa configuración activa; con candidata sustituye el snapshot por
esa definición, incluso deshabilitada. Etiquetar la simulación de borrador como tal.

Mostrar etapa AutoSuppression, match, directive, ruleId/ruleVersion y evidencia de
source, externalStatus, externalReference, windowMatch, scopeMatch y eligible.
No confundir la etapa con Blackout. SUPPRESS_INTEGRATIONS no elimina el evento ni
omite correlación, recuperación o auditoría. La simulación no escribe al outbox.

## Pruebas desde navegador

1. Alta guardada sin activación; activar y comprobar coincidencia real por simulación.
2. Inicio incluido/fin excluido, otro recurso y otro tenant como negativos.
3. ACTIVE/APPROVED elegibles; CANCELLED/COMPLETED no suprimen aun habilitados.
4. Guardar cancelación v2 mientras v1 APPROVED activa; mostrar que v1 sigue vigente
   hasta activar v2; no informar «cancelado» antes de ese cambio efectivo.
5. Reintento/doble clic no duplica versión; dos pestañas no se sobrescriben.
6. Desactivar/retirar, historial, recarga y persistencia; retiro bloquea reactivación.
7. Referencia externa visible en detalle y evidencia; no afirmar aprobación remota.
8. Errores HTTP explícitos y guardado/activación parcial correctamente representados.

Guardar evidencia en `evidences/auto-suppression/frontend-<fecha>/`. Referencias:
`testing/certifications/processor-auto-suppression-certification.py`,
`services/event-processor/src/main/resources/contracts/auto-suppression-v1.schema.json`
y `docs/event-processor/auto-suppression.md`. Sincronización externa sigue en DP-EP-05;
seguridad continúa diferida. Entregar resultados de navegador y archivos modificados.
