# Prompt para frontend — AIOps independiente

Conectar el CRUD y la consulta manual de AIOps en la consola existente. Reutilizar
proxy same-origin `/api/processor/v1` hacia `event-processor:8082/api/v1`, componentes
comunes y manejo de errores, pero respetar este contrato propio: no es una regla
versionada de `/rules`. No modificar pipeline, eventos, comandos ni seguridad.

## Fuente y API

Catálogo de lectura `/api/catalog/views/aiops-extensions` devuelve items,truncated,
mode:read_only. Filas id,tenant,name,enabled,revision provienen de aiops_configuration
sin bajas lógicas. Límite 2000 filas. Para CRUD y paginación por tenant usar API AIOps.
Todas las llamadas llevan X-Tenant-Id del tenant elegido; X-Actor-Id es metadata local,
no identidad autenticada. No usar localhost del navegador ni SQL directo.

| Acción | Ruta relativa al proxy | Cuerpo / respuesta |
|---|---|---|
| Crear | POST `/aiops` | `{id,name,enabled}`; 201 configuración y ETag |
| Listar | GET `/aiops?limit=50&after=` | Array de configuraciones; no objeto items/next |
| Leer | GET `/aiops/{id}` | 200 `{id,name,enabled,revision}` y ETag |
| Actualizar | PUT `/aiops/{id}` | `{name,enabled}`; 200 configuración y ETag |
| Baja lógica | DELETE `/aiops/{id}` | Sin cuerpo; 204 |
| Consultar mock | POST `/aiops/{id}/assessments` | `{resource,summary,severity}`; 200 resultado |

Listas ordenadas por ID, limit 1..100, after exclusivo: usar último ID recibido para
siguiente página hasta obtener menos de limit (o página vacía). Baja no aparece en
listas ni GET, conserva ID y auditoría y no admite recreación. Namespace propio de
AIOps por tenant, independiente del registro de reglas.

POST de configuración requiere If-Match `"0"`; PUT/DELETE requieren ETag vigente.
No hay reason obligatorio, version, enable/retire ni recibos Idempotency-Key.
Tras respuesta incierta, leer GET/revisión y reconciliar antes de repetir; no inventar
reintentos seguros basados en una clave ignorada por el backend. No enviar id en PUT.
Guardar enabled cambia directamente la habilitación de consultas, sin segunda activación.

Errores: 404 lectura ausente, 409 revisión obsoleta/ID duplicado o borrado/configuración
deshabilitada al consultar; 428 falta If-Match; 400/413/422 estructura/tipos/límites;
503 AIOPS_PROVIDER_UNAVAILABLE para error, timeout o respuesta inválida del proveedor.
No convertir 503 en recomendación vacía exitosa. GET no encontrado en otro tenant es
404; una mutación que no coincide con fila/revisión puede devolver 409.

## Formulario y consulta

Configuración: ID 1..64 caracteres, empieza alfanumérico y admite alfanuméricos,
punto, guion y guion bajo; nombre no vacío hasta 128; enabled booleano.
No ofrecer endpoint, credenciales ni selección de proveedor real. Crear deshabilitada
por defecto permite al operador habilitar conscientemente la consulta.

Señal: resource no vacío hasta 256, summary no vacío hasta 1024, severity entero 0..5.
No enviar severity como string. Petición máxima 8192 caracteres. Mostrar botón de
consulta sólo cuando está habilitada; manejar 409 si cambió en otra sesión.

Respuesta:

```json
{"configurationId":"bridge-local","revision":2,"provider":"INTERNAL_MOCK",
 "assessment":{"recommendation":"INVESTIGATE","confidence":0.75}}
```

Recomendación admite OBSERVE/INVESTIGATE; confidence numérico 0..1. Mostrar de manera
visible que es un mock sintético, sin inferencia real, ni compatibilidad certificada
con Kyndryl Bridge. Consultar no remedia, genera tickets ni participa automáticamente
en el pipeline. No presentar habilitación como activación automática para eventos.

La revisión de respuesta es la leída al iniciar la consulta; deshabilitar no cancela
una llamada en curso. Señales/resultados no se guardan: no prometer historial de
assessments tras recarga. El historial de cambios existe en DB, pero no tiene endpoint
público en este corte; no reutilizar `/rules/{id}/history` para AIOps.

El adaptador limita conexión a 2s y solicitud a 3s, sin reintentos/redirecciones.
Evitar envíos duplicados mientras carga; mantener formulario editable tras error.
Mostrar éxito del guardado separado de éxito de la consulta al mock.

## Aceptación desde navegador

Crear deshabilitada, recargar, habilitar mediante PUT, consultar y mostrar resultado
sintético con revisión; probar edición, conflicto 409 con ETag obsoleto, bloqueo al
deshabilitar, baja y rechazo de reutilizar ID. Verificar tenant y paginación. Rechazar
severidad string/fuera de rango y campos vacíos. Probar visualización de 503 mediante
fixture local acotado; no alterar el mock compartido globalmente. Conservar evidencia
HTTP/capturas en evidences, sin declarar PASS sólo por ejecutar certificaciones backend.

DP-EP-01/09 mantienen seguridad, Bridge real, historial de consultas y eventual
integración al pipeline pendientes. Este prompt no amplía ese alcance.
