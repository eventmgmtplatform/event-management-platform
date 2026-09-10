# Changelog — Kafka / topics

Definición del inventario de topics de eventos, comandos, resultados y automatización.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Administración `emctl kafka`: inventario, configuración, topics, grupos, drift y estado.
- Receta independiente de Kafka/Kafbat con digests, identidad KRaft por instalación,
  recursos y puertos propios, manifiesto de integridad e instalador reutilizable.
- Manual de Web UI, configuración del producto, CLI y gates de certificación futura.
- Modo de inventario sin conexión en el inicializador compartido; creación existente conservada.

- Cambios locales al inventario de topics para solicitudes de estado ESS. Este registro describe scripts, no el estado de los brokers desplegados.

## Historial confirmado en Git

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `infrastructure/kafka/create-topics.sh`.

### 2026-07-31 — `30817ba8cd32`

- Cambio registrado: chore: initialize event management platform repository.
- Alcance en este componente: `infrastructure/kafka/create-topics.sh`.
