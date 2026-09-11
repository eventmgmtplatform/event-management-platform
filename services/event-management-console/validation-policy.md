# Policy Engine — frontend 2026-09-10

Desplegado en http://localhost:8090/policies.

- Create saved inactive; reopening reads persisted definition
- Active v1 SUPPRESS_INTEGRATIONS at severity 1; NO_MATCH/CONTINUE at severity 5
- Saved v2 preserves v1 active; stale second tab HTTP 409 with draft retained; enable v2 STATE_ONLY
- High priority CONTINUE and lower priority STATE_ONLY both displayed; resolved STATE_ONLY in ACTIVE and CANDIDATE modes
- JSON validation returns visible HTTP 422 for string severity, unknown state field, and unsafe regex
- Disable/retire, history and terminal activation guard; another tenant has no active rules
- Legacy catalog separate read-only, no Processor mutation controls; selected legacy tenant empty

Metadata description/tags preservada, comprobada por lectura HTTP. Políticas sintéticas retiradas. Evidencia en `evidences/policy/frontend-20260910/`. Las capturas se emitieron en la tarea; la corroboración HTTP está identificada por separado.

Archivos: `src/modules/policy/*`, router, Header, en.json y `testing/services/event-management-console/policy.test.mjs`. Cliente/proxy e historial compartidos con Blackouts. No se modificó el motor.

Regresión final: seleccionar borrador en CANDIDATE y volver a ACTIVE con ID vacío permite simular reglas activas; resultado CONTINUE / NO_ACTIVE_RULES. No valida el borrador al ejecutar ACTIVE.
