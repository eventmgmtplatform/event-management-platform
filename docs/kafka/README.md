# Kafka de Event Management OpenSource

Administración del bus local y preparación reproducible de un candidato de producto.
El paquete conserva el broker KRaft de un nodo y los nueve topics actuales. **No es
una certificación de producción ni instala los demás servicios de Event Management.**

- [Comandos CLI aplicados al producto](cli.md)
- [Configuración, conexiones y Web UI](configuration-and-webui.md)
- [Instalación y camino a una versión certificada](installation-and-certification.md)
- [Historial del componente](../../infrastructure/kafka/CHANGELOG.md)

## Fuentes de configuración

| Fuente | Responsabilidad |
|---|---|
| `.env` en la raíz (local, ignorado por Git) | Imágenes e identidad KRaft del runtime actual |
| `infrastructure/docker-compose.yml` | Servicios actuales kafka, kafka-init y kafka-ui; listeners, almacenamiento y healthchecks |
| `infrastructure/kafka/create-topics.sh` | Inventario único de topics, particiones, replicación y retención; `--inventory` no conecta a Kafka |
| `infrastructure/kafka/compose.json` | Receta independiente Kafka/UI, derivada del Compose actual; JSON válido de Docker Compose |
| `infrastructure/kafka/images.lock.json` | Referencias inmutables a las imágenes observadas; estado candidato y arquitectura |
| `scripts/kafka-admin.py` | Lecturas administrativas, detección de drift y preparación/instalación de paquetes |
| `scripts/emctl` | Entrada del producto; enlace al controlador `eventmanagement-services.sh` |
| `<paquete>/runtime.env` | Identidad nueva, puertos e imágenes de una instalación independiente |
| `<paquete>/manifest.json` | SHA-256 de los archivos del paquete; permite detectar cambios accidentales |

La receta independiente mantiene la configuración del broker salvo identidad y
dirección anunciada del host. Cambia los puertos publicados a loopback, elimina
nombres globales de contenedores/red/volumen, fija imágenes por digest y desactiva
el asistente dinámico de Kafbat. El healthcheck del candidato usa el listener
interno `kafka:29092`: el listener del host anuncia un puerto publicado que no
existe dentro del contenedor. La prueba de paridad verifica los demás parámetros
del broker/UI frente al Compose actual. Cambiar el Compose actual exige revisar
la receta y volver a generar/certificar un candidato.

El runtime actual mantiene sus archivos y contenedores; preparar o instalar un
candidato no migra aplicaciones ni copia mensajes, offsets o volúmenes.

## Inventario del producto

| Topic | Particiones | Réplicas | Retención | Flujo |
|---|---:|---:|---:|---|
| events.raw | 3 | 1 | 7 días | Gateway → Processor |
| events.normalized | 3 | 1 | 7 días | Salida del Processor |
| events.state.requested | 3 | 1 | 30 días | Processor → ESS |
| events.lifecycle | 3 | 1 | 30 días | Canal lifecycle; verificar productores de la versión a certificar |
| integration.commands | 6 | 1 | 7 días | Comandos → Worker / admisión CACF |
| integration.results | 6 | 1 | 30 días | Resultados → ESS |
| integration.callbacks | 3 | 1 | 7 días | Canal declarado; su existencia no certifica integración |
| event.journal | 3 | 1 | 30 días | Canal declarado de journal |
| events.dlq | 1 | 1 | 14 días | Rechazos de procesamiento/admisión |

Todos usan `cleanup.policy=delete`. El estado observado del broker se consulta con
`emctl kafka topics`; `emctl kafka verify` compara con el inventario. No altera
particiones, retención ni offsets y no exige ausencia de topics adicionales.

## Operación y límites

PostgreSQL es la autoridad de estado; Kafka es transporte con retención finita.
El orden por clave no es un orden global entre topics. Una outbox puede publicar
otra vez después de un fallo; la idempotencia del consumidor sigue siendo necesaria.
Una instalación limpia crea el bus, no recupera el estado de negocio.

La plataforma conserva un único broker/controller, replicación 1 y PLAINTEXT.
Kafbat actual tiene autenticación deshabilitada. El candidato se limita a loopback;
no debe publicarse como servicio multiusuario sin diseñar acceso, identidad y TLS.
No hay migración automática a HA ni declaración de RPO/RTO.
