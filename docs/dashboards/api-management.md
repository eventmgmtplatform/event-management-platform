# API Management (07)

Ruta `/dashboards/api-management`, puerto 8091, mismo Nginx y contenedor `event-management-console`. Reutiliza `frontend-management-api` y su controlador CLI; no se crea otro daemon Docker ni Nginx.

`GET /api/administration/apis` consulta en paralelo los endpoints HTTP del registro explícito `services/frontend-management-api/apis.py`. Cada fila indica API, servicio propietario, consumidores, interfaz de negocio, URL comprobada, estado UP/DOWN/UNREACHABLE, código HTTP, latencia y fecha. Son comprobaciones de disponibilidad HTTP, no transacciones de negocio ni certificación de las credenciales del proveedor.

El registro cubre Gateway, Processor, Worker/CACF, Event State, ServiceNow, GNM, AIOps, NEXT, catálogo, mock de tickets WebUI, dashboards, administración y OpenSearch del entorno local. NEXT puede resultar inaccesible si su overlay no está levantado. Los mocks se identifican con sus nombres reales: su disponibilidad no implica disponibilidad de ServiceNow/Everbridge de producción. Si se cambia un endpoint consumido, se debe actualizar este registro explícito con la URL de salud correspondiente. No se escanean redes ni se aceptan URLs proporcionadas por el navegador.

`POST /api/administration/apis/actions` recibe `{id, apiId, action}`:

- `testing`: GET al endpoint de comprobación, timeout 2 s, sin seguir redirecciones ni enviar eventos de prueba al negocio.
- `stop` y `start`: resuelven el servicio en el servidor y llaman el controlador existente.
- `reboot`: se traduce a `restart` del servicio; no reinicia el host.

Las acciones de ciclo afectan todas las APIs alojadas por el servicio. La lista de capacidades excluye servicios fuera de la CLI y al propio controlador. Allí sólo se ofrece testing. No se simula el arranque o apagado de un SaaS externo. El estado visual se actualiza después de consultar el resultado real de la operación.

Se reutiliza `control.py`: lista cerrada de servicios y acciones, argumentos sin interpolación shell, UUID idempotente y una operación a la vez, ejecución asíncrona, resultado persistente en `/data/operations.sqlite` sobre `console-control-data`. Se consulta con `GET /api/administration/operations/{id}`. Tras un reinicio, operaciones que estaban ejecutándose pasan a interrupted y no se repiten automáticamente. Estados failed/interrupted deben investigarse, no se muestran como éxito.

El POST exige JSON, cabecera `X-Console-Action: 1`, origen coincidente cuando se proporciona y cuerpo máximo 1024 bytes. El Nginx reenvía Host para conservar esta comprobación en 8091. Se conserva el modelo de acceso del hosting local existente; el módulo no añade autenticación de usuarios. No expone credenciales, salida de comandos ni inspecciones Docker completas.

Pruebas: `python3 -m unittest discover -s services/frontend-management-api/tests -v`. El smoke HTTP `scripts/dashboards/verify-collection.py` verifica testing/stop/start/reboot contra `servicenow-console-mock` y deja el servicio iniciado; registra evidencia privada bajo `.local/oem-dashboards`.
