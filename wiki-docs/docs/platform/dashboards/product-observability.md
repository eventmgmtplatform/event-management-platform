# Observabilidad del producto en OpenSearch

Dashboard nativo: `http://localhost:5601/app/dashboards#/view/product-observability`.
Administración → Herramientas especializadas → OpenSearch Dashboards abre esta vista.

El servicio `product-observability` consulta cada 30 segundos las APIs internas
`/api/administration/platform` y `/api/administration/apis`. Indexa únicamente campos
permitidos: versiones, puertos, estado del runtime y healthcheck, reinicios, endpoint,
HTTP y latencia de sondeo. No copia variables de entorno ni credenciales.

`UP` significa HTTP 2xx, no certificación funcional de dependencias. Los mocks
conservan sus nombres. Kafka y PostgreSQL aparecen en inventario; no se inventan
APIs HTTP para ellos. Las versiones y puertos faltantes se dejan vacíos.

El índice `product-observability-current` conserva la última observación por ID.
El dashboard abre una ventana de 2 minutos con refresco de 30 segundos; al fallar
una fuente, sus observaciones previas caducan de la vista en hasta 2 minutos.
El panel de fuentes informa `UNREACHABLE`. Si todo el recolector se detiene,
los paneles quedan sin datos al vencer la ventana. Ampliar el filtro temporal
puede mostrar observaciones antiguas: revisar siempre su fecha. No hay histórico.
El índice usa un shard y cero réplicas para el laboratorio single-node existente.

## Instalación reproducible

Desde la raíz del repositorio:

```bash
python3 scripts/dashboards/build-product-observability.py
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d --build --no-deps product-observability
curl --fail-with-body -X POST 'http://localhost:5601/api/saved_objects/_import?overwrite=true' -H 'osd-xsrf: true' --form file=@infrastructure/opensearch/product-observability.ndjson
```

Los IDs `product-observability*` son estables. La importación reemplaza únicamente
estos objetos. El dashboard también se puede importar desde Management → Saved
objects. Para detener la recolección, usar `docker compose --env-file .env -f
infrastructure/docker-compose.yml stop product-observability`.

Validación: `python3 -m unittest discover -s testing/services/product-observability -v`.
La implementación usa la [API oficial de Saved Objects](https://opensearch-project.github.io/OpenSearch-Dashboards/docs/openapi/saved_objects/).
