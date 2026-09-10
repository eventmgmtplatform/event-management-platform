# Validación E2E desde OpenWebUI

La frase **«ejecuta la prueba de validación E2E»** se conecta mediante un servidor
OpenAPI a `python3 testing/run.py happy-path`. Usa UC-001 en `os11-lifecycle`:
Gateway → Kafka → Processor → Worker → ServiceNow/GNM/NEXT → ESS/OpenSearch,
incluyendo recuperación, cierres, duplicados y confirmaciones de proveedores mock.
No certifica proveedores reales ni interacciones de navegador.

## Preparación del servidor

Requiere Python 3.10+, Docker CLI/Compose y acceso al laboratorio OS_11 ya preparado.
Ejecutar en el host del repositorio; el caso utiliza sus puertos loopback y Docker.
La cuenta operativa debe poder ejecutar el runner. No montar el socket Docker en OpenWebUI.

1. Preparar el laboratorio si falta, según [testing](../../testing/README.md#orquestación-os_11).
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

Con cuenta administradora, abrir **Admin Settings → External Tools** y añadir un
servidor OpenAPI. La ubicación exacta depende de la versión instalada; consultar
la [integración oficial](https://docs.openwebui.com/features/extensibility/plugin/tools/openapi-servers/open-webui/).

- URL base: dirección privada del servidor accesible desde el backend de OpenWebUI.
- Esquema: `/openapi.json`.
- Autenticación: Bearer con el mismo `E2E_TOOL_TOKEN`, incluido al cargar el esquema.
- Habilitar sólo para los operadores de validación.
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
Aclara que UC-001 utiliza el laboratorio aislado con proveedores mock.
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

Sólo hay dos operaciones: iniciar el caso fijo y leer un resultado. No admite
comandos, rutas ni parámetros del modelo. Solicitudes de inicio concurrentes
reutilizan el caso en curso. Tras finalizar, un nuevo inicio crea otra ejecución.
No debe exponerse como una herramienta que reintenta POST automáticamente.
La consulta continúa disponible después de reiniciar el servidor.

Si el servidor se interrumpe con un caso activo, lo marca INTERRUPTED y bloquea
nuevos casos. Un operador debe comprobar que el runner haya terminado, inspeccionar
su reporte y la limpieza de sus reglas, y archivar fuera de este directorio el
registro interrumpido antes de reiniciar el servicio. No cambiarlo a PASS manualmente.
La exclusión cubre esta API; no ejecutar otra certificación CLI en paralelo.

Verificación del puente (no ejecuta UC-001):

```bash
python3 -m unittest discover -s testing/services/e2e-tool-api -v
```
