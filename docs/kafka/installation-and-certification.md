# Instalación reproducible y certificación futura

## Unidad de distribución

El candidato es un **paquete de configuración con dos imágenes upstream fijadas
por digest**, no una imagen monolítica que contenga broker, UI y datos. Kafka y
Kafbat mantienen ciclos de vida separados; el volumen Kafka queda fuera de las
imágenes. El producto completo también necesitará sus servicios, contratos y
migraciones PostgreSQL, que este instalador no incluye.

`images.lock.json` registra la referencia observada del broker 4.3.1 y el digest
de la UI que estaba desplegada como `latest`. No se deduce una versión semántica
de Kafbat a partir de esa etiqueta. La plataforma fijada es linux/amd64; otra
arquitectura requiere su propia validación.

Apache distribuye su [imagen oficial JVM](https://kafka.apache.org/43/getting-started/docker/).
El paquete utiliza esa distribución y conserva la configuración local conocida.
Fijar el digest identifica el contenido a evaluar; no demuestra seguridad,
compatibilidad ni aprobación de release.

## Instalación desde cero

1. Disponer de Linux amd64, Bash, Python 3, Docker Engine y Compose v2 con `up --wait`.
   El instalador no instala Docker ni cambia permisos del host.
2. Desde el checkout, ejecutar `emctl kafka prepare` con directorio, proyecto y
   puertos propios. El comando no depende del `.env` privado del entorno actual.
3. Revisar `compose.json`, `runtime.env`, `images.lock.json` y `manifest.json` del
   paquete. El preparador crea un Cluster ID nuevo de 16 bytes en Base64 URL-safe.
4. Ejecutar `emctl kafka install --directory ...`. Comprueba hashes, propiedad de
   proyecto/volumen, arranca el broker y espera salud. Ejecuta el inicializador,
   compara los nueve topics y después arranca/espera la UI.
5. Consultar `inspect`, `verify` y abrir el puerto Web UI. Conservar el paquete
   como identidad de la instalación. Ver comandos completos en [CLI](cli.md).

No se eliminan datos ante fallo ni se oculta un estado parcial. El mismo paquete
puede reintentarse. Una revisión de receta o imagen se prepara en otro directorio
y proyecto; actualizar una instalación existente necesita un procedimiento de
upgrade/rollback separado. `--no-recreate` evita que repetir el instalador aplique
un upgrade implícito. [Semántica de Compose up](https://docs.docker.com/reference/cli/docker/compose/up/).

El inventario se copia del mismo `create-topics.sh` usado por el runtime actual.
El script crea topics ausentes y conserva existentes; la verificación posterior
rechaza diferencias en lugar de cambiar retención o particiones silenciosamente.

## Configuración estable por instalación

| Variable del paquete | Origen / regla |
|---|---|
| COMPOSE_PROJECT_NAME | `--project`, prefijo em-kafka- |
| KAFKA_IMAGE / KAFKA_UI_IMAGE | images.lock.json, digest obligatorio |
| KAFKA_CLUSTER_ID | Generado una sola vez; conservar con volumen |
| KAFKA_HOST_PORT | `--broker-port`, 19092 por defecto |
| KAFKA_UI_PORT | `--ui-port`, 18085 por defecto |
| KAFKA_UI_CLUSTER_NAME | Nombre del proyecto |

El wrapper elimina overrides KAFKA_* y COMPOSE_* heredados del shell al invocar
Docker. El destino de Docker sigue siendo el contexto/socket del usuario: revisar
`docker context show` antes de instalar en otra máquina.
Los hashes detectan cambios accidentales; **no son una firma de distribución**.

## Qué falta para certificar una versión de producto

| Gate | Evidencia exigida |
|---|---|
| Instalación limpia | Broker/UI saludables, inventario y configuración efectiva comparados |
| Persistencia | Publicar/leer registro, reiniciar y recrear contenedor conservando volumen; volver a leer |
| Continuidad de consumidores | Offsets y grupos preservados con clientes del producto |
| Fallos | Broker no disponible, UI no disponible, reintentos/outbox y recuperación sin pérdida silenciosa |
| Contratos | Gateway → Processor → Worker → ESS y lifecycle sobre las imágenes candidatas |
| Operación | Lag/alertas, disco/retención, backup/restore integral y objetivos RPO/RTO acordados |
| Seguridad y distribución | Perfil de acceso/TLS, revisión de vulnerabilidades, SBOM, licencias y avisos upstream |
| Release | Versionado, digests de todos los componentes, firma/procedencia y publicación en registro aprobado |
| Upgrade/rollback | Matriz de compatibilidad Kafka/clientes, formato de datos y procedimiento probado |

La prueba `testing/harness/test_kafka_admin.py` comprueba fronteras del instalador,
integridad, aislamiento de nombres/puertos, drift y paridad de receta. No reemplaza
las pruebas Kafka reales. La certificación acotada en
`testing/certifications/kafka-certification.py` se ejecuta **sólo** contra un paquete
aislado; conserva su registro sintético y evidencia. No certifica el flujo de negocio.

Los resultados de cada ejecución van a `evidences/kafka/`, ignorado por Git.

```bash
python3 -m unittest discover -s testing/harness -p test_kafka_admin.py -v
python3 testing/certifications/kafka-certification.py \
  --directory /tmp/em-kafka-demo --restart
```

`--restart` reinicia y después recrea únicamente el broker del candidato con el
mismo volumen. El escenario publica en un topic `em.certification.<id>` propio,
lee explícitamente partición/offset sin usar grupos de aplicaciones y vuelve a
leer después de ambas operaciones. Conserva el topic; su mensaje tiene retención
de un día. Sin el flag se omiten las pruebas de reinicio/recreación.

El código, receta, digests y criterios sí se versionan. El estado de release se
mantiene CANDIDATE_NOT_CERTIFIED hasta completar y aceptar los gates del corte elegido.
