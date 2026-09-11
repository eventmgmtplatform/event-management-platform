# Configuración de Kafka y Kafbat Web UI

## Runtime en uso

El Compose principal resuelve `.env` explícitamente desde `scripts/emctl`.
Los servicios son `kafka`, `kafka-init`, `kafka-ui`; sus contenedores actuales se
llaman `event-kafka`, `event-kafka-init`, `event-kafka-ui`.

| Parámetro | Valor actual declarado | Dónde |
|---|---|---|
| Imagen broker | apache/kafka:4.3.1 | `.env`: KAFKA_IMAGE |
| Imagen UI | ghcr.io/kafbat/kafka-ui:latest | `.env`: KAFKA_UI_IMAGE |
| Cluster ID | Identidad persistente propia del entorno | `.env`: KAFKA_CLUSTER_ID → CLUSTER_ID del contenedor |
| Roles / nodo / quorum | broker,controller / 1 / 1@kafka:9093 | Compose, environment de kafka |
| Listener de aplicaciones | PLAINTEXT://kafka:29092 | KAFKA_ADVERTISED_LISTENERS |
| Listener del host | PLAINTEXT_HOST://localhost:9092 | KAFKA_ADVERTISED_LISTENERS |
| Controller | 0.0.0.0:9093, no publicado en host | KAFKA_LISTENERS |
| Replicación y min ISR | 1 | KAFKA_*REPLICATION_FACTOR, KAFKA_MIN_INSYNC_REPLICAS |
| Auto creación / borrado | false / true | KAFKA_AUTO_CREATE_TOPICS_ENABLE / KAFKA_DELETE_TOPIC_ENABLE |
| Particiones por defecto | 3; el inicializador fija cada topic | KAFKA_NUM_PARTITIONS |
| Retención general | 168 horas | KAFKA_LOG_RETENTION_HOURS |
| Revisión de retención / segmento | 300000 ms / 1073741824 bytes | KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS / KAFKA_LOG_SEGMENT_BYTES |
| Datos | /var/lib/kafka/data | KAFKA_LOG_DIRS y montaje kafka-data |
| Volumen físico Docker | event-management-kafka-data | `volumes.kafka-data.name` del Compose |
| Red Docker | event-management-net | `networks` del Compose |

El Cluster ID no es una credencial. Debe mantenerse junto al volumen existente;
generar otra identidad corresponde a un clúster nuevo, no a un reinicio.
La retención explícita de cada topic prevalece sobre el valor general del broker.

## Integración de aplicaciones

| Aplicación | Configuración de conexión / grupo |
|---|---|
| Gateway | `EVENT_GATEWAY_KAFKA_BROKERS=kafka:29092`, topic `EVENT_GATEWAY_KAFKA_TOPIC=events.raw` |
| Processor | `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`; grupo `enrichment-engine` conservado por compatibilidad |
| Worker | `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`; grupo `integration-worker` |
| ESS resultados | Bootstrap común; grupo `event-state-service` |
| ESS solicitudes de estado | Bootstrap común; grupo `event-state-service-lifecycle` |
| CACF | Bootstrap común; grupo `cacf-admission`, sólo cuando la función está habilitada |

Los valores y contratos se declaran en `services/<servicio>/src/main/resources/application.properties`
y sus rutas Camel. Conservar los grupos al reemplazar una imagen mantiene la
continuidad de offsets; `earliest/latest` sólo decide qué hacer si no existe un
offset válido. No es una instrucción para hacer replay de todo el histórico.

Deuda observada: el Compose actual del Worker declara `KAFKA_TOPIC_COMMANDS` y
`KAFKA_TOPIC_RESULTS`, pero sus propiedades esperan `KAFKA_TOPIC_INTEGRATION_COMMANDS`
y `KAFKA_TOPIC_INTEGRATION_RESULTS`. Los nombres actuales funcionan por defaults.
Antes de personalizar topics, alinear ambas capas y certificar a todos los clientes.

Un cliente en Ubuntu utiliza `localhost:9092`. Un contenedor de la red principal
utiliza `kafka:29092`; su `localhost` apuntaría al propio contenedor. El paquete
independiente anuncia `localhost:19092` por defecto para clientes en su host.
Integrar aplicaciones en su red privada requiere un despliegue de plataforma
coordinado; el instalador no redirige las aplicaciones activas.

## Acceso y uso de la Web UI

Runtime actual: [Kafbat UI](http://localhost:8085).
Paquete independiente por defecto: [Kafbat candidato](http://localhost:18085).
Desde otro equipo, `localhost` designa ese otro equipo: usar un túnel al host
Docker para acceder a la instalación candidata, publicada sólo en loopback.

1. Abrir el clúster `event-management-local` (o el nombre del proyecto candidato).
2. En **Brokers**, revisar el único nodo, disponibilidad y configuración expuesta.
3. En **Topics**, buscar un nombre del inventario y revisar particiones, réplicas,
   configuración y retención. Comparar con `emctl kafka verify`.
4. En la vista de mensajes, seleccionar topic, partición y offset/intervalo acotado.
   Las claves corresponden normalmente a `eventKey`; los contratos actuales usan
   JSON serializado como texto. No se configura Schema Registry en esta instalación.
5. En **Consumer Groups**, localizar `enrichment-engine`, `integration-worker` o
   los grupos ESS y revisar miembros, offsets y lag por partición. Un grupo vacío
   puede corresponder a un servicio detenido; no implica por sí solo pérdida de datos.

Ejemplo de diagnóstico: si `integration.results` acumula lag, consultar el grupo
`event-state-service`, después logs de ESS y dependencias PostgreSQL/OpenSearch.
La salud HTTP del broker/UI no demuestra progreso del consumidor. Un lag cero es
una observación puntual, no una garantía de procesamiento de negocio correcto.

La UI actual permite operaciones de escritura: crear/borrar topics, publicar
mensajes y modificar offsets/configuración según las funciones de la versión.
Esas acciones alteran el bus del producto; la consulta de mensajes no requiere
resetear grupos de aplicaciones. Para ensayar escrituras usar el candidato aislado.

## Configuración propia de Kafbat

| Variable | Actual | Candidato |
|---|---|---|
| KAFKA_CLUSTERS_0_NAME | event-management-local | Nombre em-kafka-* preparado |
| KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS | kafka:29092 | kafka:29092 en su red propia |
| DYNAMIC_CONFIG_ENABLED | true | false |
| SWAGGER_UI_ENABLED | true | true |
| AUTH_TYPE | DISABLED | DISABLED, publicado sólo en loopback |
| MANAGEMENT_HEALTH_LDAP_ENABLED | false | false |
| Healthcheck | GET /actuator/health dentro del contenedor | Igual |

El asistente dinámico guarda cambios en `/etc/kafkaui/dynamic_config.yaml` y archivos
asociados. El Compose actual **no monta un volumen para esa carpeta**: recrear la
UI puede perder esos cambios. La configuración reproducible del candidato se
mantiene en archivos y deshabilita el asistente; esto no vuelve la UI de sólo
lectura para operaciones Kafka. Véase la [documentación del asistente Kafbat](https://ui.docs.kafbat.io/configuration/configuration-wizard).

No se ha configurado un exporter JMX/Prometheus, Kafka Connect ni Schema Registry.
Que la UI admita esas integraciones no implica que estén instaladas.
Para un futuro perfil de consulta, Kafbat dispone de `KAFKA_CLUSTERS_0_READONLY`;
debe versionarse y validarse con el perfil de acceso elegido.
Referencias: [propiedades Kafbat](https://ui.docs.kafbat.io/configuration/misc-configuration-properties),
[health y arranque](https://ui.docs.kafbat.io/overview/getting-started).
