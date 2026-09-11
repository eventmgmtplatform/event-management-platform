# Sistema: instancias de integración y Secrets

Estado: propuesta de diseño, 2026-09-10. No constituye implementación ni despliegue.

## Navegación y propósito

Sistema: Administración, Ticketing, Notifications, Automations, AIOps Extensions, Cliente, Secrets, en ese orden.
Mover Automations desde /automations; mantener ruta compatible. Mover AIOps Extensions y Cliente conservando rutas y comportamiento. Nuevas rutas propuestas: /system/ticketing, /system/notifications, /system/secrets. Plugins mantiene Tickets, Notifications y CACF como vistas de operación. Sistema configura las conexiones utilizadas por esas operaciones.
El Dashboard general deberá reflejar la misma distribución, evitando listas independientes que diverjan del registro de navegación.

## Base existente comprobada

- infrastructure/postgres/init/001-initialize-event-management.sql: integration_configuration tiene integration_id UUID, tenant, integration_name, integration_type, base_url, authentication_type, credential_reference, configuration JSONB, enabled y timestamps; unicidad tenant/nombre. Reutilizar mediante migración, sin crear otro catálogo equivalente. La existencia del DDL no prueba que el Worker resuelva dinámicamente esta tabla: verificar migraciones y consumidores antes de implementar.
- Worker tiene clientes ServiceNow, GLPI y GNM, y CacfSettings para NEXT: URL, usuario, contraseña, api-token y límites. Hoy varios valores se inyectan mediante configuración del proceso; guardar un formulario no cambia por sí mismo ese runtime.
- Terraform Secret Manager administra contenedores/metadatos e IAM, no carga valores ni implementa una API de Secrets para esta consola.
- El perfil ROUTING base certificado está limitado a SERVICENOW/default. Un registro GLPI, GNM o CACF en Sistema no amplía automáticamente el contrato de Routing. Conectar referencias a los consumidores es un incremento backend explícito.
- AIOps sigue teniendo contrato propio, independiente de /rules. Su traslado de menú no incorpora credenciales/proveedor real a ese formulario.

## Formularios de instancias

Patrón común: tabla con cliente, nombre, proveedor, entorno, habilitación, revisión y última verificación. Filtros por cliente/proveedor/estado; alta deshabilitada; abrir, editar, validar, guardar y deshabilitar. Diferenciar configuración guardada, configuración aplicada, conectividad comprobada y resultado de ejecución.

Secciones: Identificación; Conexión; Autenticación; Valores de integración; JSON opcional. Campos compartidos: tenant/cliente, nombre, entorno, proveedor, URL base, usuario o clientId cuando corresponda, esquema de autenticación y referencias de secretos. Límites y campos derivados del adaptador, no formularios universales con cualquier parámetro.

Ticketing: selector ServiceNow/GLPI. Campos específicos de entidad/organización, grupo de asignación, categoría y valores permitidos por cada contrato. No confundir grupo con credencial. Password, userToken, appToken o clientSecret siempre referencias en selectores separados según autenticación admitida.
Notifications: GNM; organización, grupo de notificación y demás valores no sensibles admitidos por el adaptador. Credenciales mediante Secrets.
Automations: CACF/NEXT; URL, usuario, Secret de contraseña y Secret de API token; límites HTTP, acknowledgement y resultado ya soportados. Los parámetros de automatización se validan contra su contrato; no ofrecer payload o comandos arbitrarios.

Importación JSON: pegar/subir documento limitado por tamaño; validar schemaVersion y proveedor; mostrar errores por campo; poblar un borrador y previsualizar diferencias; confirmar guardado por separado. Rechazar password/token/clientSecret en claro. Aceptar credentialBindings con referencias verificables del mismo tenant/entorno; no crear secretos a partir del JSON. Exportación sin valores sensibles. Cambiar proveedor no descarta silenciosamente campos: mostrar diferencias antes de confirmar.

## Secrets

Formulario simple: Cliente, Entorno, Nombre, Tipo (password/token), Valor protegido, Guardar. El backend genera UUID opaco y registra versión. Al volver a abrir, solo metadatos; no devolver valor. Operaciones: crear, reemplazar mediante nueva versión, deshabilitar; mostrar referencias en uso y bloquear borrado destructivo mientras existan dependencias.

Identidad lógica compuesta: tenant + entorno + nombre; unicidad de esa tupla. Referencia técnica: secretId + versión. El identificador no es una clave de cifrado ni una autorización. No derivar una clave criptográfica concatenando nombres, tenant o contraseña.

PostgreSQL almacena exclusivamente metadatos, referencias de versiones y auditoría. Valores en un gestor de secretos mediante adaptador backend. Para GCP reutilizar Secret Manager existente; para localhost seleccionar un almacén compatible dedicado antes de aceptar contraseñas reales, sin criptografía casera ni claves maestras en la misma DB/repositorio. Interfaz de repositorio independiente del proveedor. No provisionar recursos cloud como parte de este diseño.

Combobox muestra nombre, entorno, tipo, versión y disponibilidad; jamás carga la contraseña. Filtrado y autorización también en backend. El formulario Secrets envía el valor una sola vez sobre transporte protegido; no logs de cuerpo, localStorage, sessionStorage, exports ni respuestas con plaintext. El Worker resuelve únicamente secretos autorizados de la instancia y versión seleccionadas, sin exponerlos al evento, Kafka o Explain.

La consola actual es local y X-Actor-Id es metadata, no identidad autenticada. Antes de almacenar secretos reales, implementar acceso autenticado, autorización por tenant y permisos separados para administrar metadatos/escribir valores/resolver valores. Los selectores no son controles de autorización. El navegador no dispone de operación para recuperar el valor guardado.

Referencia: https://docs.cloud.google.com/secret-manager/docs/best-practices (mínimo privilegio, API directa, versiones explícitas y auditoría de acceso).

## Modelo y API propuestos

Reutilizar integration_configuration, añadiendo revisión optimista, entorno, proveedor explícito cuando integration_type no lo distinga y bajas lógicas. La separación de tipo TICKETING/NOTIFICATIONS/AUTOMATIONS y proveedor SERVICENOW/GLPI/GNM/NEXT debe migrar valores existentes explícitamente. Conservar nombre/UUID existentes.

Agregar relación integration_credential_binding: integration_id + propósito (password/apiToken/appToken/etc.) + secret_id + secret_version; validar tenant/entorno compatibles en servidor. Compatibilidad/migración de credential_reference, evitando dos fuentes de verdad.
Agregar metadatos secret y versiones referenciadas en gestor; auditoría sin valores. Configuración por proveedor con JSON Schema y schemaVersion. Preservar historial/versiones de instancia para reproducibilidad; ETag/If-Match para evitar sobreescrituras.

API same-origin propuesta, aún no disponible:
- /api/administration/integration-instances: listado paginado por tenant/tipo, alta y lectura/edición por ID.
- /api/administration/integration-instances/{id}/validate: validación sin efecto externo.
- /api/administration/integration-instances/{id}/check: comprobación de conexión limitada, sin crear tickets/notificaciones/ejecuciones. Sin solicitudes a destinos arbitrarios, redirects ni respuesta remota en bruto.
- /api/administration/secrets: listado de metadatos y alta protegida.
- /api/administration/secrets/{id}/versions: reemplazo protegido, respuesta solo metadata.
No crear endpoint público de lectura de valores. Permisos y ámbito explícitos en todos los endpoints.

## Composición del evento

Cliente aporta customerCode/bamId/gnmOrgId y referencias permitidas. Enrichment selecciona instancia de integración y valores de destino no sensibles (grupo, categoría, organización), con revisión de configuración. Routing y orquestación determinan acciones conforme a su contrato. El Worker valida y resuelve configuración/secretos al ejecutar. Credenciales, URL libre y material secreto nunca se copian desde el evento ni se incluyen en comandos.

Ejemplo conceptual, no payload actual de Routing:
{"customerCode":"CLIENTE-01","integration":{"instanceId":"snow-operacion","revision":3,"assignmentGroup":"operaciones"}}
Instancia referencia passwordSecretId/version; esa referencia se administra en backend. Rotación no requiere enriquecer de nuevo los eventos históricos. Definir explícitamente la política de versión de credencial para reintentos; no cambiar destinos o identidad semántica de comandos históricos.

## Secuencia de implementación

1. Reorganizar navegación y Dashboard general, manteniendo rutas existentes.
2. Migración/API de instancias y contrato de Secrets; resolver almacén local y control de acceso.
3. Secrets y Ticketing ServiceNow/GLPI: formulario/JSON, persistencia, aislamiento, conflictos, export seguro.
4. Notifications GNM y Automations CACF con selectores y campos específicos.
5. Conectar referencias desde enriquecimiento a consumidores existentes, con pruebas de compatibilidad; no declarar aplicado solo por guardar.
6. Probar conexiones contra mocks aislados, errores reales, secreto inexistente/deshabilitado, rotación, redacción y no filtración a eventos/logs. Certificación real de proveedores por separado.
