# Validación E2E desde OpenWebUI

La frase **«ejecuta la prueba de validación E2E»** se conecta mediante un servidor
OpenAPI a `python3 testing/run.py happy-path --runtime shared`. Usa UC-001 en el runtime compartido, con tenant sintético y proveedores mock:
Gateway → Kafka → Processor → Worker → ServiceNow/GNM/NEXT → ESS/OpenSearch,
incluyendo recuperación, cierres, duplicados y confirmaciones de proveedores mock.
No certifica proveedores reales ni interacciones de navegador. También expone `registrar_blackout`, que crea y lee de vuelta una ventana SCHEDULED de exactamente 60 minutos con alcance `customerCode` + `node` (servidor).

## Uso configurado

Abrir [Validación E2E](http://localhost:3000/?models=validacion-e2e) y escribir
**ejecuta la prueba de validación E2E**. El preset privado incluye `qwen3:1.7b`,
la herramienta `server:validacion-e2e`, modo Native, contexto Ollama de 16384 tokens
y herramientas internas desactivadas. Su instrucción exige una llamada real y
reportar el runtime devuelto. No es necesario activar manualmente la herramienta
cuando se utiliza este preset.
La instrucción exacta guardada está en [system-prompt.txt](system-prompt.txt).
El preset separado [Gestión de Blackouts](http://localhost:3000/?models=gestion-blackouts)
usa exclusivamente `registrar_blackout`, para que una solicitud de mantenimiento
no pueda confundirse con la validación E2E. Su instrucción está en
[blackout-system-prompt.txt](blackout-system-prompt.txt).
Limita la respuesta a campos del reporte para evitar duraciones calculadas o
interpretaciones incorrectas de las comprobaciones.

El comando fija `--runtime shared`: no depende del valor por defecto del runner.
La prueba exige la configuración sintética del Worker; genera eventos y reglas
propios y conserva sus evidencias. No solicita reinicios de servicios.

## Preparación del servidor

Requiere Python 3.10+, Docker CLI/Compose y acceso al runtime shared configurado para proveedores mock.
Ejecutar en el host del repositorio; el caso utiliza sus puertos loopback y Docker.
La cuenta operativa debe poder ejecutar el runner. No montar el socket Docker en OpenWebUI.

En este host, `python3 scripts/e2e-tool-deploy.py` instala el servicio de usuario
`eventmanagement-e2e-tool.service`, habilita su arranque con la sesión y verifica
el esquema. Descubre la puerta de enlace de `open-webui_default` y escucha sólo
en esa interfaz (E2E en `http://172.18.0.1:8095` y blackouts aislados en
`http://172.18.0.1:8097`). Guarda el token,
el entorno y la conexión importable bajo `.local/e2e-tool/`, con permisos privados
y exclusión de Git. No imprime el secreto. Requiere acceso al Docker y systemd
del usuario; no usar `sudo` para crear archivos pertenecientes a root.

1. Verificar la [activación shared](../../docs/cacf/shared-activation.md). El runner exige endpoints mock y configuración sintética antes de ejecutar.
2. Configurar `E2E_TOOL_TOKEN` con un secreto aleatorio de al menos 32 caracteres,
   mediante el mecanismo de secretos del supervisor. No guardarlo en Git ni en chats.
3. Ejecutar `python3 services/e2e-tool-api/server.py` desde este checkout.
   Por defecto escucha `127.0.0.1:8095`. Para operación persistente usar un supervisor
   con directorio de trabajo `/opt/event-management-platform` y parada ordenada.
4. Hacer que el backend de OpenWebUI alcance esa dirección. En un contenedor,
   `localhost` corresponde al contenedor: requiere un proxy privado hacia el host
   o una interfaz privada configurada con `E2E_TOOL_HOST`. No publicar en Internet.
   Una conexión remota debe usar HTTPS. El puerto es configurable con `E2E_TOOL_PORT`.

## Configuración en OpenWebUI

Con cuenta administradora, abrir **Admin Panel → Settings → Integrations → External Tool Servers** y añadir un
servidor OpenAPI. La ubicación exacta depende de la versión instalada; consultar
la [integración oficial](https://docs.openwebui.com/features/extensibility/plugin/tools/openapi-servers/open-webui/).

- URL base: dirección privada del servidor accesible desde el backend de OpenWebUI.
- Esquema: `/openapi.json`.
- Autenticación: Bearer con el mismo `E2E_TOOL_TOKEN`, incluido al cargar el esquema.
- Habilitar sólo para los operadores de validación.
- En este host se puede importar `.local/e2e-tool/connection.json` en el formulario
  de conexión. Mantener **Access: Private**, verificar conexión y guardar.
- Importar también `.local/e2e-tool/blackout-connection.json`; su esquema sólo
  publica `registrar_blackout`. Asociarlo al preset **Gestión de Blackouts**.
- Recargar la página después de guardar. En el chat, abrir **Integrations → Tools**
  y activar **Validación E2E**: guardar una conexión no la activa en el chat.
- Seleccionar un modelo ya configurado con soporte de herramientas, activar las
  herramientas del servidor y la llamada de funciones nativa.

Instrucción para el modelo asociado:

```text
Cuando el usuario pida «ejecuta la prueba de validación E2E», llama una vez a
ejecutar_validacion_e2e. Conserva el runId devuelto y consulta
consultar_validacion_e2e para conocer el resultado. RUNNING es una ejecución en
curso, nunca una aprobación. Si continúa en curso tras unas consultas, informa
su runId y permite al usuario pedir «consulta el resultado E2E»; no inicies otra
ejecución. No inventes comprobaciones ni resultados. Sólo informa aprobado si la
herramienta devuelve PASS. Resume alcance, checks, identidades y reportPath.
Ante FAIL, BLOCKED o INTERRUPTED, informa el error y no reintentes automáticamente.
Aclara que UC-001 utiliza el runtime compartido con tenant sintético y proveedores mock; informa el campo runtime del reporte. No describas este entorno como infraestructura aislada.
```

## Aceptación del procedimiento

1. Escribir la frase en un chat con estas herramientas activadas.
2. Comprobar la llamada real a `ejecutar_validacion_e2e` y conservar su `runId`.
3. Consultar hasta estado terminal. El resultado debe contener `reportPath`, el
   reporte UC-001, código de salida, identidades y checks. Si la llamada no ocurre,
   revisar selección de herramientas y capacidad del modelo.
4. Contrastar con `evidences/testing/<ejecución>/happy-path/report.json` y
   `SHA256SUMS`. Los registros de la integración están en
   `evidences/testing/openwebui/<runId>.json` y `.log`.

Hay tres operaciones: iniciar el caso fijo, leer un resultado y registrar un
blackout de 60 minutos. Ninguna admite comandos, rutas ni parámetros arbitrarios.
Solicitudes de inicio concurrentes
reutilizan el caso en curso. Tras finalizar, un nuevo inicio crea otra ejecución.
No debe exponerse como una herramienta que reintenta POST automáticamente.
La consulta continúa disponible después de reiniciar el servidor.
Cada llamada espera hasta 20 segundos por un resultado terminal; si sigue en curso,
devuelve RUNNING y se consulta con el mismo runId, sin lanzar otra prueba.

Para registrar un blackout desde el chat, escribe por ejemplo:

```text
Registra un blackout para el customer CUST-01 y el servidor app-01 durante 60 minutos.
```

El modelo llama a `registrar_blackout`, que crea una ventana SCHEDULED desde ahora
hasta +60 minutos, con `scope.customerCode=CUST-01` y `scope.node=app-01`, y luego
lee la regla para confirmar el registro. Si falta customer o servidor, debe pedirlo.
La herramienta rechaza cualquier duración distinta de 60, campos extra, comandos
o URLs. No crea registros de prueba automáticamente porque customer y servidor
son datos operativos del usuario.
La instrucción tiene prioridad de seguridad: cualquier mensaje que contenga
«blackout» o «ventana de mantenimiento» nunca dispara la validación E2E. Si faltan
customer o servidor, el asistente sólo los solicita y no invoca ninguna operación.

Si el servidor se interrumpe con un caso activo, lo marca INTERRUPTED y bloquea
nuevos casos. Un operador debe comprobar que el runner haya terminado, inspeccionar
su reporte y la limpieza de sus reglas, y archivar fuera de este directorio el
registro interrumpido antes de reiniciar el servicio. No cambiarlo a PASS manualmente.
La exclusión cubre esta API; no ejecutar otra certificación CLI en paralelo.

Verificación del puente (no ejecuta UC-001):

```bash
python3 -m unittest discover -s testing/services/e2e-tool-api -v
```
