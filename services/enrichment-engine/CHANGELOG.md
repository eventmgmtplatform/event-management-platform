# Changelog — enrichment-engine

Predecesor del Processor: foundation y paso de events.raw a events.normalized en agosto. El commit de septiembre que introduce event-processor retira/sustituye este árbol.

[Índice y política](../../docs/changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- Componente histórico sustituido; no reactivado. Consultar el changelog de event-processor para la evolución vigente.

## Historial confirmado en Git

### 2026-09-09 — `baf778df7160`

- Cambio registrado: feat(event-processor): replace enrichment with durable processing foundation.
- Alcance en este componente: `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentCommitProcessor.java`, `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentEngineRoute.java`, `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentPassThroughProcessor.java`, `services/enrichment-engine/src/main/resources/application.properties` y 3 archivo(s) adicional(es).

### 2026-09-07 — `fc51edcaa23a`

- Cambio registrado: chore(integration): capture accumulated project workstreams.
- Alcance en este componente: `services/enrichment-engine/Dockerfile`.

### 2026-08-13 — `741f631080e1`

- Cambio registrado: feat(os-01-01): add events enrichment pass-through.
- Alcance en este componente: `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentCommitProcessor.java`, `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentEngineRoute.java`, `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentPassThroughProcessor.java`, `services/enrichment-engine/src/main/resources/application.properties`.

### 2026-08-13 — `cff1e46e3a28`

- Cambio registrado: feat(os-01-01): add enrichment engine foundation.
- Alcance en este componente: `services/enrichment-engine/src/main/java/com/eventmanagement/enrichment/EnrichmentEngineRoute.java`, `services/enrichment-engine/src/main/resources/application.properties`, `services/enrichment-engine/.dockerignore`, `services/enrichment-engine/Dockerfile` y 1 archivo(s) adicional(es).
