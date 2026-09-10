# Frontend Management API

BFF local de inventario para la consola existente en `http://localhost:8090/administration`.
Python estándar, Docker Compose y Nginx; sin dependencias Python externas.

## Flujo

Navegador → Nginx de `event-management-console` → `frontend-management-api:8093` → inspección Docker por socket Unix.

`GET /api/administration/platform` devuelve un snapshot nuevo. No publica puerto al host. El catálogo explícito en `server.py` contiene los nombres de los 19 componentes del runtime principal y excluye los entornos de certificación. Incluir un nuevo componente requiere agregarlo al catálogo. Los componentes esperados ausentes se mantienen visibles con error.

`GET /health` es liveness del BFF. La salud del runtime se determina por el endpoint de inventario. Si Docker falla para todo el catálogo, responde HTTP 503; si falla solo una inspección, ese componente queda sin verificar con diagnóstico `docker_unavailable`.

| Condición | Estado |
| --- | --- |
| Running + healthy | healthy |
| Unhealthy, dead, restarting, salida fallida | error |
| Ausente | error / missing |
| Detenido | stopped |
| Pausado o healthcheck iniciando | degraded |
| Running sin healthcheck | unknown |
| kafka-init terminó con código 0 | completed |

La versión es la etiqueta de la imagen ejecutada; las imágenes sin etiqueta explícita devuelven null. No se usan etiquetas de versión heredadas del sistema operativo base. El puerto es el menor puerto publicado; null significa que no hay puerto publicado verificable. La salud es la reportada por los healthchecks existentes, no una certificación de todas las funciones de negocio.

## Frontera local

La consola publica `0.0.0.0:8090` para acceso local y desde la LAN. El BFF tiene filesystem de solo lectura, capacidades eliminadas salvo DAC_OVERRIDE (lectura del .env protegido montado en solo lectura) y no-new-privileges. No permite shell, comandos Docker ni rutas de inspección arbitrarias; solo GET para los nombres fijos del catálogo. No devuelve variables de entorno, logs de healthcheck, montajes ni credenciales.

El montaje `docker.sock:ro` no convierte la API de Docker en una API de solo lectura: el proceso del BFF conserva acceso privilegiado al daemon. La restricción de lectura está implementada en su código. Este despliegue es local; antes de abrirlo a otros usuarios se requiere una frontera de autorización de Docker y autenticación de la consola.

## Operación

Desde la raíz del repositorio:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml build frontend-management-api event-management-console
docker compose --env-file .env -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.itsm-dashboard.yml up -d --no-deps --wait frontend-management-api event-management-console
bash scripts/eventmanagement-services.sh health frontend-management-api
PYTHONPATH=services/frontend-management-api python3 -m unittest discover -s services/frontend-management-api/tests -v
```

Actualizar solo estos dos servicios conserva el runtime y los volúmenes. Para rollback, ejecutar Compose con las imágenes anteriores de ambos componentes; detener solo el BFF deja la consola disponible pero el inventario informa que no puede verificar el runtime.

## Fuentes de la consola

GET `/api/administration/sources` verifica acceso a Docker y health de `servicenow-console-mock`. Devuelve dos conexiones fijas con identidad, destino, endpoint y salud. Ninguna escritura a ServiceNow pasa por el BFF con acceso a Docker: Nginx enruta GET/PATCH del plugin directamente al mock aislado.

## Control de servicios desde el detalle

POST `/api/administration/operations` recibe `{id, service, action}`. `id` es UUID de idempotencia; action admite start, stop o restart. El controlador ejecuta exactamente:

```bash
EVENTMANAGEMENT_RUNTIME=local bash /opt/event-management-platform/scripts/eventmanagement-services.sh SERVICE ACTION
```

Reutiliza validación de Compose, dependencias, conservación de datos, stop/start y healthchecks del script. No usa comandos recibidos del navegador, shell interpolado, operaciones globales, down ni runtimes de certificación. La lista permitida está en `control.py`. Las solicitudes requieren JSON, cabecera X-Console-Action y origen coincidente cuando se envía Origin; el servicio sigue siendo local sin autenticación multiusuario.

Una sola operación se ejecuta a la vez. SQLite en `event-management-console-control-data` registra ID, servicio, acción, inicio, final, estado y exit code. GET `/api/administration/operations/ID` permite consultar el resultado. Si la API se reinicia, marca las operaciones pendientes interrupted sin repetirlas. Los argumentos se validan antes de crear el trabajo. El navegador conserva el ID para recuperar el seguimiento al abrir de nuevo el detalle.

El contenedor necesita Docker CLI/Compose, bash/jq y los scripts, infraestructura y .env del proyecto montados en solo lectura, con las mismas rutas absolutas del host para resolver los bind mounts. El volumen de operaciones es escribible. No se devuelve stdout/stderr del CLI ni secretos al navegador. Un resultado failed exige revisar el estado real y ejecutar el mismo CLI para diagnóstico; el API no promete rollback.

La propia API de control, las tareas one-shot y los componentes fuera de Compose se administran por CLI. Detener la consola también desconecta sus dashboards: la interfaz avisa que necesitará CLI para encenderla de nuevo.

ITSM Dashboard identifica la superficie actual del contenedor compartido en 8091. ITSM Dashboard legacy conserva la identificación del contenedor anterior en 8088. La superficie de 8091 comparte ciclo de vida con Management Console.
