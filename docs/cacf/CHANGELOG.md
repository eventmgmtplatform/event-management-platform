# Changelog — CACF / NEXT

Foundation local de automatización: admisión REST/Kafka, ejecución persistente, adaptador NEXT/XML, callbacks/ACK, asociación de ticket, outcomes y outbox; integra acciones ITSM.

[Índice y política](../changelogs/README.md). Reconstrucción al 2026-09-10 desde Git local: fechas de autor y SHA verificables; no equivalen a releases o despliegues. Los cambios sin commit se separan en Unreleased.

## Unreleased — corte documental 2026-09-10

- OS_11_01.IMP: Processor solicita CACF tras GNM confirmado y conserva identidad de grupo/ciclo más referencias fuente. REMEDIATED mantiene monitoreo abierto; clear espera resultado y nota. UNKNOWN/fallos conservan revisión. No cambia seguridad XML ni semántica de ACK, deadlines, TKTUPDATE o callbacks terminales de CACF.

- Certificación y ambiente aislado movidos a testing, escenario enfocado REMEDIATED y documentación UC-001. Resultados históricos extraídos de documentación a evidences; éxito CACF continúa siendo distinto del clear del monitoreo.

## Historial confirmado en Git

### 2026-09-08 — `3d45006cbf23`

- Cambio registrado: feat(os-05-cacf): implement local automation foundation and code documentation.
- Alcance en este componente: `services/integration-worker/src/main/java/com/eventmanagement/integration/ServiceNowAutomationActionProcessor.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationRepository.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationRequest.java`, `services/integration-worker/src/main/java/com/eventmanagement/integration/cacf/AutomationResource.java` y 28 archivo(s) adicional(es).
