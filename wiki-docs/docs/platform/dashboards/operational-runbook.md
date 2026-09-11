# Preparación y ejecución local

## Base PostgreSQL

Aplicar `infrastructure/postgres/init/018-oem-dashboard-views.sql` con `psql -v
ON_ERROR_STOP=1 -f ...` en la base existente, después de 002/008/017. Requiere
propietario de tablas/esquema y permiso de crear roles. Los scripts de init sólo
se ejecutan automáticamente en volúmenes nuevos: un volumen existente necesita
esta migración explícita. No reinicializar volúmenes.

018 crea cuatro vistas y el rol de grupo NOLOGIN `oem_dashboard_reader`.
El administrador debe provisionar un LOGIN separado con contraseña mediante el
gestor de secretos y otorgarle `GRANT oem_dashboard_reader TO <login>;`.
No otorgar permisos sobre tablas operativas ni usar el usuario propietario en el BFF.
Las vistas no requieren que el lector tenga acceso a los payloads de las tablas base.

## Compose

Agregar al entorno local (no al repositorio):

```dotenv
OEM_POSTGRES_DSN=postgresql://<login>:<secret>@postgres:5432/<database>
OEM_INTERNAL_API_URL=http://<internal-oem-query-api>:<port>
OEM_INTERNAL_API_TOKEN=<optional-server-token>
```

La API interna todavía debe ser provista con el contrato descrito en contracts.md.
El modo PostgreSQL funciona con las vistas; no requiere esperar a esa API.

```bash
bash scripts/emctl ui oem-dashboards start
bash scripts/emctl ui oem-dashboards health
bash scripts/emctl ui oem-dashboards source get
bash scripts/emctl ui oem-dashboards source set postgresql
bash scripts/emctl ui oem-dashboards source set api --dashboard gnm
bash scripts/emctl ui oem-dashboards source test --dashboard gnm
bash scripts/emctl ui oem-dashboards smoke-test
```

Abrir `http://localhost:8091`. Puerto 8091 publicado por el servicio Console en Compose base. El enlace
de Console usa el puerto de laboratorio 8091. Console y Dashboards comparten el contenedor `event-management-console` y su Nginx.
La imagen se construye con `services/event-management-console/Dockerfile.shared`.
El overlay sólo agrega el BFF interno, no otro servidor Nginx.
El overlay nuevo se combina con
Compose base sólo en este namespace CLI; los comandos globales antiguos conservan
su alcance. `start` construye el BFF y la imagen compartida de Console/Dashboards y no arranca ni
reinicia dependencias. PostgreSQL debe estar disponible antes. La recreación de Console produce una interrupción breve en 8090/8091. El BFF no publica puerto
en el host; Nginx sirve UI/API al mismo origen. Configuración persistida en volumen
`oem-dashboard-config`, owned por UID 10001. Source set ejecuta dentro del BFF para
compartir exactamente configuración, credenciales y red. Sin destino válido, falla
con exit 1 y conserva configuración anterior. No imprime secretos.

`stop` detiene sólo el BFF, conservando el Nginx compartido. `restart`/`reload`
reinician BFF y Console (ambos puertos); `logs`/`status` consultan ambos.
`health` consulta readiness, no sólo liveness. Cambio de fuente no necesita reload;
cambio de variables de entorno requiere recrear mediante `start`.
El servidor HTTP es foundation local; no exponer este overlay a producción.

## Desarrollo sin contenedores

Python 3.12 y Node 22, con dependencias instaladas:

```bash
python3 -m venv .local/oem-venv
.local/oem-venv/bin/pip install -r services/oem-dashboards-api/requirements.txt
# Exportar OEM_POSTGRES_DSN y/o OEM_INTERNAL_API_URL desde el entorno privado.
.local/oem-venv/bin/python services/oem-dashboards-api/server.py
# Otra terminal, desde services/oem-dashboards:
npm ci
npm run dev
```

Vite :8091 hace proxy al BFF :8092. `OEM_DASHBOARD_CONFIG` selecciona archivo local;
por defecto `/tmp/oem-dashboards/config.json`. CLI local:

```bash
.local/oem-venv/bin/python services/oem-dashboards-api/cli.py source set api --dashboard ticketing
```

## Pruebas y reversión

```bash
node --test testing/services/oem-dashboards/data.test.mjs
.local/oem-venv/bin/python testing/services/oem-dashboards/test_dashboard.py -v
.local/oem-venv/bin/python testing/services/oem-dashboards/integration.py
# Desde services/oem-dashboards:
npm run build
```

Runner central con evidencias: `python3 testing/run.py dashboards` y
`python3 testing/run.py dashboards-integration` (usar el Python del venv con psycopg).

Integración crea y elimina exclusivamente PostgreSQL temporal con puerto aleatorio
en loopback. Prueba SQL real, reejecución de migración, role grant, filtros,
paginación, permisos, paridad API/PostgreSQL y rollback de configuración.
Los fixtures sintéticos son sólo pruebas, no ingesta de plataforma.

Reversión del frontend: `bash scripts/emctl ui oem-dashboards stop`; ITSM/Console
continúan operando. Conservar volumen de configuración y vistas aditivas. Para volver
a una fuente anterior, usar source set (con prueba); `.previous.json` conserva último
archivo. No hay eliminación de datos, roles, volúmenes ni down migration automática.

## Despliegue con demostración

`python3 scripts/dashboards/deploy.py` respalda el schema y conserva la imagen
anterior de Console, aplica 008/017/018 de forma aditiva, provisiona un usuario
de lectura, carga la demo aislada, construye y despliega sobre el mismo Nginx.
Verifica default PostgreSQL y datos de los cuatro dominios mediante HTTP.
Credenciales: `.local/oem-dashboards/runtime.env` con permisos 0600 (ignorado por Git),
cargado automáticamente por el namespace CLI; nunca copiarlo a documentación.
El resumen sin secretos se guarda en `.local/oem-dashboards/deployment.json`.
