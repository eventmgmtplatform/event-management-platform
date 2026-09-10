# CLI de Kafka aplicada a Event Management OpenSource

Ejecutar desde la raíz del checkout. `bash scripts/emctl` funciona aunque no esté
instalado un comando global. También se puede usar `python3 scripts/kafka-admin.py`.
La preparación requiere Python 3 y Bash; la operación requiere Docker Engine,
Docker Compose v2 y permiso de acceso a Docker. Los comandos de ciclo de vida de
`emctl` requieren además jq. No instalar estos requisitos con scripts remotos implícitos.

## Consulta del Kafka actual: no modifica datos

```bash
# Configuración declarada sólo del subsistema Kafka (sin mostrar otros servicios).
bash scripts/emctl kafka config

# Inventario esperado; no requiere daemon Docker.
bash scripts/emctl kafka inventory

# Contenedores, imagen configurada, image ID y salud observada.
bash scripts/emctl kafka inspect
bash scripts/emctl kafka status
bash scripts/emctl kafka health
bash scripts/emctl kafka-ui status
bash scripts/emctl kafka-ui health

# Configuración real de topics; compara particiones, réplicas, cleanup y retención.
bash scripts/emctl kafka topics
bash scripts/emctl kafka verify

# Offsets/lag de todos los grupos o de uno concreto.
bash scripts/emctl kafka groups
bash scripts/emctl kafka groups --group enrichment-engine
bash scripts/emctl kafka groups --group integration-worker
bash scripts/emctl kafka groups --group event-state-service
bash scripts/emctl kafka groups --group event-state-service-lifecycle

# Logs acotados.
bash scripts/emctl kafka logs
bash scripts/emctl kafka-ui logs
EVENTMANAGEMENT_LOG_TAIL=200 bash scripts/emctl event-state-service logs
```

`config` muestra la declaración resuelta, no cambios dinámicos del broker/UI.
`topics` consulta el broker; `verify` falla si hay diferencias, sin intentar repararlas.
`inspect` muestra el runtime observado. `groups` no hace commits ni resetea offsets.
Las acciones especializadas actuales aceptan `EVENTMANAGEMENT_RUNTIME=local|ecosystem`;
rechazan otro runtime para evitar consultar por error el clúster principal.

Ejemplo de diferencia: `events.state.requested: ausente` significa que el repositorio
ya lo declara pero ese broker no lo tiene. Registrar la diferencia y coordinar el
despliegue del contrato/consumidor correspondiente; `verify` no crea el topic.

Lectura de la salida de grupos: `CURRENT-OFFSET` es el offset confirmado;
`LOG-END-OFFSET` es el final observado del log; `LAG` es su diferencia.
Revisar cada partición y el progreso entre observaciones. `-` puede indicar ausencia
de offset o miembro: no interpretarlo como cero. CACF puede no tener grupo activo
si su admisión no está habilitada en este runtime.

## Acciones sobre servicios actuales: cambian disponibilidad

```bash
bash scripts/emctl kafka start
bash scripts/emctl kafka-ui start
bash scripts/emctl kafka-ui restart

# Sólo en una ventana operativa: interrumpe temporalmente el bus.
bash scripts/emctl kafka restart
bash scripts/emctl kafka stop
```

`start` respeta dependencias; `stop` conserva contenedores y volúmenes.
`restart` detiene y arranca. `reload` recrea según configuración y puede aplicar
cambios de imagen; no es necesario para consultar. No usar acciones globales
`emctl stop/restart` para una consulta Kafka: afectan a la plataforma completa.
`kafka-init` con salida cero representa una inicialización terminada correctamente.

## Preparar e instalar un Kafka nuevo

```bash
# Sólo genera archivos; no toca Docker ni lee/copia el .env de la plataforma.
bash scripts/emctl kafka prepare \
  --directory /tmp/em-kafka-demo \
  --project em-kafka-demo \
  --broker-port 19092 --ui-port 18085

# Revisar la configuración exacta del paquete.
bash scripts/emctl kafka config --directory /tmp/em-kafka-demo

# Crea únicamente Kafka/UI, red y volumen del nuevo proyecto.
bash scripts/emctl kafka install --directory /tmp/em-kafka-demo

# Consultar explícitamente el candidato.
bash scripts/emctl kafka inspect --directory /tmp/em-kafka-demo
bash scripts/emctl kafka topics --directory /tmp/em-kafka-demo
bash scripts/emctl kafka verify --directory /tmp/em-kafka-demo
bash scripts/emctl kafka groups --directory /tmp/em-kafka-demo
```

`prepare` exige un directorio nuevo y no sobrescribe identidad ni archivos.
Los puertos 9092/8085 están reservados al entorno principal por el preparador.
`install` exige `--directory`, valida hashes y crea sólo recursos `em-kafka-*`.
Una repetición sobre el mismo paquete conserva identidad y volumen; detecta
diferencias de topics, pero no las corrige. No actualiza contenedores existentes
ni borra recursos al fallar. Revisar el error/log antes de repetir.

## Usar el paquete fuera del repositorio

Copiar el directorio completo a un host Linux amd64 con Python 3, Bash, Docker y
Compose v2. No necesita el `.env` de la plataforma ni el resto del checkout.

```bash
python3 /tmp/em-kafka-demo/kafka-admin.py install --directory /tmp/em-kafka-demo
python3 /tmp/em-kafka-demo/kafka-admin.py verify --directory /tmp/em-kafka-demo

# Ciclo de vida del paquete: indicar siempre proyecto, env y Compose.
docker compose -p em-kafka-demo --env-file /tmp/em-kafka-demo/runtime.env \
  -f /tmp/em-kafka-demo/compose.json ps -a
docker compose -p em-kafka-demo --env-file /tmp/em-kafka-demo/runtime.env \
  -f /tmp/em-kafka-demo/compose.json logs --tail 100 kafka kafka-ui
docker compose -p em-kafka-demo --env-file /tmp/em-kafka-demo/runtime.env \
  -f /tmp/em-kafka-demo/compose.json stop kafka-ui kafka

# Volver a encender con las comprobaciones del instalador.
python3 /tmp/em-kafka-demo/kafka-admin.py install --directory /tmp/em-kafka-demo
```

El nombre `em-kafka-demo` debe coincidir con `runtime.env`. No sustituir el archivo
por el Compose principal. El volumen del ejemplo es `em-kafka-demo_kafka-data`.
Detenerlo no libera su almacenamiento. Conservar paquete e identidad para reiniciar.

## CLI nativa de Apache Kafka: consultas equivalentes del runtime actual

```bash
docker exec event-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --describe --topic integration.results

docker exec event-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --group event-state-service --describe

docker exec event-kafka /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server kafka:29092 --entity-type brokers --entity-name 1 --describe

docker exec event-kafka /opt/kafka/bin/kafka-metadata-quorum.sh \
  --bootstrap-server kafka:29092 describe --status
```

`kafka-configs --describe` sin `--all` muestra overrides dinámicos, no sustituye el
inventario completo de configuración. No usar `--alter`, `--delete` ni
`--reset-offsets --execute` como diagnóstico. Una modificación requiere definir
el cambio, su efecto en retención/replay y actualizar la fuente versionada.

## Errores frecuentes

| Síntoma | Acción |
|---|---|
| Docker no accesible | Verificar daemon y permisos del usuario; no cambiar automáticamente permisos del socket |
| Paquete cambió | Preparar otro candidato y revisar cambios; no recalcular hashes para ocultar diferencias |
| Proyecto/volumen pertenece a otra identidad | Usar proyecto nuevo; no borrar ni reformatear el volumen encontrado |
| Puerto ocupado | Preparar otro directorio con puertos libres |
| Drift de topics | Comparar esperado/actual, documentar migración; no hay reparación automática |
| UI saludable pero lag crece | Revisar consumidor, outbox y dependencias; no resetear offsets |
| Imagen no disponible | Verificar acceso al registro o precargar exactamente el digest fijado |

Los comandos devuelven cero al completar su operación y distinto de cero ante
fallo; un PASS de inventario no equivale a certificación funcional del producto.
