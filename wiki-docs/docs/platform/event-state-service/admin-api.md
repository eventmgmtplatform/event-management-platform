# API administrativa ESS — lectura v1

Base local: `http://127.0.0.1:8084/api/v1/state`.
PostgreSQL es la fuente autoritativa. No modifica estados, reenvía eventos ni
reconstruye OpenSearch. No devuelve payloads originales ni resultados completos.

| GET | Parámetros | Permiso |
|---|---|---|
| `/events` | `limit` (1–100, defecto 50), `after` (event_key, máximo 128) | Token del tenant |
| `/event` | `eventKey` obligatorio | Token del tenant |
| `/history` | `eventKey`, `afterVersion` (>=0), `limit` (1–100) | Token del tenant |
| `/quarantine` | Ninguno | Token de operador global |

Consultas por tenant requieren `X-Tenant-Id` y `X-ESS-Admin-Token`. El token está
vinculado al tenant en configuración; cambiar la cabecera no cambia el permiso.
El token de operador sólo autoriza el resumen global de cuarentena, pues la
cuarentena no tiene atribución fiable de tenant. Ese resumen contiene razón,
conteo y última fecha; nunca cuerpo, hash ni offsets del mensaje rechazado.

La lista devuelve `items` y `nextCursor` (vacío al finalizar); historial devuelve
`items` y `nextVersion` (0 al finalizar). El cursor usa orden por clave/versión,
no representa una instantánea frente a escrituras concurrentes. Los campos de
estado usan nombres PostgreSQL como `event_key`, `lifecycle_status` y `version`.
El historial corresponde a transiciones explícitas, no a todas las actualizaciones
de integraciones. Para estado ajeno o inexistente, GET event devuelve el mismo 404;
history devuelve lista vacía. No se consulta la proyección OpenSearch en esta API.

Errores JSON: 401 UNAUTHORIZED, 400 de límite/clave/cursor, 404 EVENT_NOT_FOUND,
503 ADMIN_NOT_CONFIGURED o STATE_STORE_UNAVAILABLE. Los errores SQL no se devuelven.
Las respuestas propias usan Cache-Control: no-store. Lecturas registran operación
en el log del servicio, sin tokens ni cuerpos; no hay auditoría durable de accesos
ni gestión multiusuario/RBAC. Los parámetros numéricos mal formados también pueden
ser rechazados por el framework antes de entrar al recurso.

## Credenciales locales y CLI

```bash
python3 scripts/event-state-admin.py configure
bash scripts/emctl event-state-service admin
ESS_ADMIN_TENANT=vit bash scripts/emctl event-state-service admin
python3 scripts/event-state-admin.py event --tenant os11-synthetic --event-key 'CLAVE'
python3 scripts/event-state-admin.py history --tenant os11-synthetic --event-key 'CLAVE'
python3 scripts/event-state-admin.py quarantine
```

El bootstrap genera tokens aleatorios distintos para vit, os11-synthetic y operador.
No imprime secretos ni reemplaza credenciales existentes. Archivo excluido de Git:
`.local/ess-admin/tokens.json`, bajo directorio privado 0700. El archivo es legible
por UID 1001 mediante mount de sólo lectura; el directorio padre restringe acceso
local. ESS carga el archivo al iniciar. Nuevos tenants o rotación requieren editar
esa configuración privada y reiniciar ESS. No agregar tokens al frontend, logs o
URLs. Esta entrega ofrece API y CLI; la integración visual en consola queda pendiente.

## Verificación

`StateAdminIT` prueba HTTP real con PostgreSQL aislado: autenticación, suplantación
de tenant, ocultamiento de registros ajenos, paginación, historial y permiso global
separado. La certificación ESS consume la plantilla 1.2.0, cuyo check `admin-api`
verifica lectura, rechazo de token inválido y resumen de cuarentena en runtime.

Despliegue acotado:

```bash
python3 scripts/event-state-lifecycle-deploy.py --offline-build --ess-only
```

Preserva Processor/Worker y los targets del laboratorio. El token no habilita
mutaciones; reprocesamiento, rebuild, UI y autenticación central siguen fuera de
esta primera API de consulta.

## Resultado

Desplegada con PASS: 34 pruebas Java y 10 controles de servicio. Evidencias e
identidad del despliegue en [validación](validation.md). El comando CLI se verificó
contra el runtime compartido sin mostrar credenciales.
