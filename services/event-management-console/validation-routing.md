# Routing y comandos base — aceptación de frontend

Fecha: 2026-09-10. URL desplegada: http://localhost:8090/routing.
Imagen probada: `sha256:0f106c9d55625b989cd229f76522b0f561ec71cb4a1d0887bf78b855aa1c7dbe`.
Base Git observada: `526c370`, cambios de trabajo sin commit. Build TypeScript/Vite y tests del módulo: **PASS**.

## Resultado por caso

| Caso | Resultado | Evidencia de navegador |
|---|---|---|
| Tenant obligatorio | PASS | Nueva ruta deshabilitada sin selección |
| Alta/persistencia | PASS | Alta DISABLED, revisión 1; documento nuevo en segunda pestaña lee la definición |
| Activación y versiones | PASS | v1 activa revisión 2; guardar v2 revisión 3 conserva activa v1; simulación muestra ruleVersion 1; habilitar v2 revisión 4 |
| Conflicto entre pestañas | PASS | Segunda pestaña con ETag 1 recibe 409; conserva prioridad 99 en borrador; tabla actualizada sin reemplazar editor |
| Desactivar/retirar/historial | PASS | Revisiones 5/6; estado terminal e historial visible |
| Grupo + ruta | PASS | Un evento produce un candidato; borrador sustituye la ruta seleccionada |
| Ruta sin grupo | PASS | Cero candidatos; NO_CORRELATION_CYCLE |
| Dos miembros | PASS | [1,0] candidatos; mismo commandId; EXISTING_SEMANTIC_COMMAND |
| Recuperación | PASS | Cero CREATE; con borrador GTE 0 aparece RECOVERY_OPERATION_NOT_IMPLEMENTED |
| Configuración inválida | PASS | Regex `[` recibe 422 CONFIGURATION_INVALID visible |
| Paginación mixta | PASS | 53 definiciones, 2 páginas; primera página sin ROUTING no detiene lectura; concurrencia máxima 4 |
| Lifecycle existente | PASS | Fixture deshabilitado visible con sus cinco parámetros, formulario bloqueado sin pérdida |
| Error 503 | PASS | Fixture limitado a tenant y simulaciones; mensaje HTTP explícito sin resultado exitoso; proxy restaurado |
| Español/inglés y temas | PASS | Español/IBM Carbon e inglés/Actual revisados visualmente |
| Capturas exportadas a evidences | PENDING | Capturas emitidas en conversación; exportación de PNG no disponible mediante el navegador conectado |

No se certifica recepción real del proveedor ni Lifecycle con este formulario. La consulta Explain está implementada; no se generaron eventos reales ni tickets para probarla. Persistencia se comprobó con carga de documento nuevo, sin reutilizar estado React de la primera pestaña.

## Evidencias y escenarios

[Reporte](../../evidences/routing/frontend-20260910/report.json), [observaciones de navegador](../../evidences/routing/frontend-20260910/browser-observations.txt), [secuencia HTTP separada](../../evidences/routing/frontend-20260910/http-sequence-corroboration.json), [sin grupo](../../evidences/routing/frontend-20260910/http-no-group-corroboration.json), [registro final](../../evidences/routing/frontend-20260910/group-ticket-record.json), [historial](../../evidences/routing/frontend-20260910/group-ticket-history.json). HTTP corrobora; no sustituye interacción de navegador. Escenarios usan tenant `routing-ui-20260910-1340`, recurso router-1, miembros member-a/member-b, tiempos crecientes y eventIds únicos.

Fixture 503 instalado 19:50:11Z, retirado 19:50:28Z; solo `/simulations` del tenant sintético. No se modificaron el Processor ni el mock compartido. Prueba reproducible en `testing/services/event-management-console/routing-proxy-fixture.py` (install/restore).

Limpieza: group-ticket RETIRED revisión 6; by-node RETIRED revisión 4; lifecycle-readonly RETIRED revisión 2; 50 grupos a-page-* retirados. Los registros guardados conservan ID y auditoría. Fixtures de paginación/Lifecycle preparados y retirados por HTTP; CRUD principal ejecutado desde navegador. No se enviaron comandos al Worker.

## Archivos del alcance

- `src/modules/routing/{RoutingPage,RoutingEditor,RoutingSimulation}.tsx`, `routing.model.ts`.
- Entradas puntuales en router, navegación, Header y en.json.
- `testing/services/event-management-console/routing.test.mjs` y fixture de proxy.
- Este documento, README/CHANGELOG e índice de handoffs.

Reutiliza cliente Blackouts, History, ConditionEditor y construcción de secuencias de Correlación. No incorpora cliente HTTP, backend o catálogo nuevo. Mutaciones preservan body/actor/ETag/Idempotency-Key ante incertidumbre. Los comandos se muestran como intenciones inmutables, nunca como ticket confirmado ni CRUD.

No se creó commit, tag ni publicación Git. Evidencias generadas permanecen fuera de commits. DP-EP-01/04/06/08 y ServiceNow real siguen diferidos.
