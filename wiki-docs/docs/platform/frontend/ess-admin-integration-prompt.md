# Prompt para el chat del frontend — integrar administración ESS

Trabaja en `/opt/event-management-platform`. Implementa la integración visual de
la API administrativa de Event State Service en la consola existente. Inspecciona
el estado Git y cambios concurrentes antes de editar; preserva otros frentes.
No crees otro frontend ni otro ambiente. No hagas commit/push sin autorización.

## Estado y fuentes que debes verificar

- Runtime principal: Compose `event-management`, consola localhost:8090.
- ESS: servicio interno `event-state-service:8084`, health `/health/ready`.
- CACF/GNM/NEXT con mocks ya funcionan aquí. El flujo completo y reinicio fueron
  certificados. Los laboratorios quedaron detenidos y archivados de forma recuperable;
  verifica `docs/environment-consolidation.md` para el estado final.
- Lee `docs/event-state-service/admin-api.md`, `validation.md`,
  `docs/administration-api-inventory.md`, README de la consola, Nginx y BFF actuales.
- Código de contrato: `services/event-state-service/src/main/java/com/eventmanagement/state/StateAdminResource.java`.
- Regresiones: `testing/services/event-state-service/java/com/eventmanagement/state/StateAdminIT.java`.

## API real (sólo lectura)

Base `/api/v1/state`:

1. GET `/events?limit=50&after=...`: `items` y `nextCursor` (vacío al finalizar).
2. GET `/event?eventKey=...`: estado de un evento; clave por query, no concatenar
   segmentos sin codificar. Devuelve 404 tanto si no existe como si pertenece a otro tenant.
3. GET `/history?eventKey=...&afterVersion=0&limit=50`: `items` y `nextVersion`
   (0 al finalizar). Historia de transiciones explícitas, no de todos los resultados.
4. GET `/quarantine`: `scope: operator-global` y `items` con reason/count/last_seen_at.
   No tiene tenant fiable; nunca presentarlo como una consulta filtrada por tenant.

Token de consulta vinculado a tenant: cabeceras `X-ESS-Admin-Token` y `X-Tenant-Id`.
El resumen global requiere token de operador distinto. Tokens aleatorios viven en
`.local/ess-admin/tokens.json`, excluido de Git. No imprimas ese archivo.
No enviar tokens al navegador, incluirlos en bundles/storage/URLs, ni escribirlos
en logs/evidencias. No exponerlos como configuración pública de Vite.

Campos de estado en snake_case: event_key, event_id, tenant, lifecycle_status,
ticket_number, notification_id, automation_id, servicenow_status, gnm_status,
cacf_status, source_severity, effective_severity, tally, version, first_seen_at,
last_updated_at, last_state_at. Fechas/campos pueden ser null. Historial:
message_id, event_key, from_status, to_status, transition_type,
aggregate_version, occurred_at, recorded_at. Preserva la distinción entre
estado del evento (OPEN/CLOSED) y confirmaciones de proveedor (p.ej. RESOLVED).

## Implementación esperada

- Reutiliza navegación, componentes, tema e i18n español/inglés existentes.
- Sección ESS accesible desde la administración: lista paginada por tenant,
  detalle con estados/referencias y tabla de transiciones.
- Resumen global de cuarentena como capacidad de operador claramente separada.
  No mostrar eventos rechazados ni crear botones de replay/rebuild: no hay API.
- Usa un backend/proxy de lectura en el origen de la consola. Comprueba primero
  qué backend existente tiene la responsabilidad adecuada; evita dar Docker socket
  a un componente que sólo necesita consultar ESS.
- Destino upstream fijo y rutas/métodos permitidos. GET únicamente. Valida límites
  y cursores; propaga sólo parámetros documentados. No aceptar URL destino del cliente.
- Selección de tenant limitada por configuración/autorización del servidor. El BFF
  elige el token correspondiente; no confíes en un rol/token arbitrario enviado
  desde el navegador. Protege el permiso global aparte. Conserva la frontera local
  existente y declara sus límites: no anuncies RBAC multiusuario inexistente.
- Estado vacío, carga, errores 400/401/404/503, reintento y cancelación de solicitudes
  al cambiar tenant/evento. Limpia datos anteriores inmediatamente para no mostrar
  transitoriamente información de otro tenant. Usa paginación por cursor real.
- Cache-Control no-store. No fallback automático a mocks de frontend ni datos inventados.
- No mezclar eventos de fuente con situaciones `correlation:...`: mostrar identidad
  y tipo claramente. No interpretar tally como número de tickets.

## Pruebas y aceptación

1. Pruebas de adaptador/proxy: tenant no permitido, aislamiento, rechazo de rutas
   o métodos ajenos, tokens ausentes de respuestas/logs, errores y paginación.
2. UI: lista/detalle/historial, vacío, error, cambio de tenant, fechas nulas,
   accesibilidad por teclado e i18n. Sigue la organización central en `testing/`.
3. Integración real consola → backend → ESS, verificando que la red del navegador
   no contiene tokens. Evidencias sin secretos en `evidences/`.
4. Certifica sin levantar OS_11/CACF/Kafka duplicados. Si necesitas un evento
   sintético reutiliza `python3 testing/run.py happy-path` en el entorno principal.
   No reinicies toda la plataforma para probar una vista.
5. Actualiza documentación/CHANGELOG y despliega únicamente los componentes de
   frontend/backend modificados, con rollback. No cambies el dominio ESS para
   acomodar presentaciones salvo que encuentres un defecto demostrable.

Entrega: comportamiento implementado, pruebas y evidencia, archivos afectados,
límites de autorización reales y comandos de repetición. No afirmes que la UI
certifica proveedores productivos: el ambiente actual usa proveedores simulados.
