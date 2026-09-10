# Changelog — GNM / Everbridge

Notificación y lifecycle Everbridge incorporados como checkpoint D06. Apertura/cierre, confirmación por GET, checkpoints y reconciliación se recuperan de los archivos registrados en esos commits.

[Índice y política](../changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: resultados GNM con identidad completa y resultId estable antes de persistir/publicar; coordinación exige OPEN_CONFIRMED/CLOSED_CONFIRMED y usa ticket/incidentId confirmados. Registry opcional por archivo local para tenants de laboratorio; sin credenciales en comandos. Alcance de código en el changelog del Worker.

- Mocks, fixtures y pruebas trasladados a testing. El encadenamiento ticket → alerta y clear → cierre confirmado es alcance futuro de OS_09; no se declara realizado.

## Historial confirmado en Git

### 2026-09-07 — `909f3b3712ea`

- Cambio registrado: feat(d06): implement GNM notification core and Everbridge lifecycle.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/EverbridgeGnmIncidentLookupClient.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/GnmCloseExecutionProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/GnmCloseResultProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/GnmClosedConfirmationProcessor.java` y 49 archivo(s) adicional(es).
