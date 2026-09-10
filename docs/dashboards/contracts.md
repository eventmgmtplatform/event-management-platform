# Contrato de lectura 1.0

El quinto módulo usa un DTO de configuración específico: ver
[Delivery / ActionFiltering](delivery-model.md). `GET /api/dashboards/delivery`
admite destino, APPLID, severidad, estado, cliente y búsqueda; no usa filas de eventos.

`GET /api/dashboards/{events|ticketing|gnm|cacf}`. JSON, mismo origen del navegador.
Filtros opcionales `tenant`, `status`, `q` (hasta 128 caracteres), `page` (1–100000),
`limit` (1–100, predeterminado 25). Cliente/estado son exactos. `q` busca subcadena
literal, sin distinguir mayúsculas, en id/eventId/reference. `%` y `_` no son comodines.
Filtros desconocidos/repetidos e índices fuera de rango devuelven 400.

```json
{
  "schemaVersion": "1.0",
  "domain": "events",
  "source": "postgresql",
  "observedAt": "2026-09-10T18:00:00Z",
  "lastUpdatedAt": null,
  "total": 0,
  "page": 1,
  "limit": 25,
  "counts": {},
  "rows": []
}
```

Cada fila contiene `id`, `tenant`, `status`, `eventId`, `reference`, `updatedAt`,
`severity`, `tally`, `outcome`. `reference`, `severity`, `tally`, `outcome` pueden ser
null. Severidad 0–5, tally entero no negativo. Timestamps ISO-8601 con zona.
Estados se conservan como los registró cada dominio. Conteos enteros no negativos.
Orden: updated_at descendente, id ascendente como desempate. Paginación por offset;
actualizaciones entre solicitudes pueden mover registros entre páginas.

`counts`, `total` y `lastUpdatedAt` abarcan **todo el resultado filtrado**, no sólo
la página. Suma de counts = total. PostgreSQL obtiene grupos y filas en la misma
transacción REPEATABLE READ / READ ONLY. Sin datos, counts={}, rows=[] y
lastUpdatedAt=null; observedAt sólo indica cuándo se consultó.

El adaptador interno usa `OEM_INTERNAL_API_URL` como base y agrega el mismo path y
query. Puede enviar `OEM_INTERNAL_API_TOKEN` como Bearer desde el servidor. Espera
este contrato, salvo `source`, que el BFF determina. Valida y reconstruye el DTO;
descarta propiedades adicionales. Rechaza redirecciones, HTML, JSON inválido y
respuestas mayores de 2 MB. No debe configurarse apuntando al mismo BFF: generaría
un ciclo. No incluye un mapeador de las APIs nativas de los proveedores.

`GET /api/dashboards/config` expone únicamente fuentes por dominio.
`GET /health` comprueba proceso; **no** implica conexión de datos.
`GET /ready` consulta las cuatro fuentes con límite 1; 503 si alguna falla.
`emctl ... smoke-test` prueba las cuatro fuentes desde el entorno del BFF.
API desconocida 404, mutaciones 405, fuente no disponible/contrato inválido 503.
No hay SQL, shell, ingesta, secretos ni mutaciones en el contrato público.
