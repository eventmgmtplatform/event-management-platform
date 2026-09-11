# APIs y contratos

El contrato central de referencia es [`openapi.yaml`](openapi.yaml). Describe
las rutas administrativas y de consulta expuestas por el proxy same-origin de la
consola y los contratos públicos relevantes del Processor. Las rutas internas
de workers y callbacks se documentan en sus changelogs y contratos de dominio.

## Convenciones

- Base del navegador: `/api`.
- Processor detrás del proxy: `/api/processor/v1`.
- Catálogos de lectura: `/api/catalog`.
- Dashboards: `/api/dashboards`.
- `X-Tenant-Id` delimita el tenant elegido; `X-Actor-Id` es metadata local.
- `If-Match` y `ETag` protegen mutaciones versionadas.
- Los errores usan `error`, `message` y, cuando aplica, `details`.

La especificación es deliberadamente agnóstica del proveedor. No incluye
credenciales ni secretos, y no autoriza acceso SQL directo desde el navegador.
Para probarla se debe levantar el stack local y utilizar el proxy de la consola.
