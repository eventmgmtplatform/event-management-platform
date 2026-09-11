# Frontend auto-suppression — 2026-09-10

Desplegado en localhost:8090. Aceptación interactiva en navegador independiente de certificación backend.

- Browser: save v1 without activation -> NO_MATCH; activate APPROVED -> MATCH
- Browser: inclusive start, exclusive end, other resource and other tenant
- Browser: save CANCELLED v2 leaves APPROVED v1 executing; enable v2 -> eligible false
- Browser: ACTIVE v3 -> MATCH including CLOSE; COMPLETED v4 -> NO_MATCH including candidate preview
- Browser: double clicks produce one audit action; stale second tab -> HTTP 409 REVISION_CONFLICT, draft preserved
- Browser: scoped HTTP 503 during enable showed saved/activation pending; reload retained identical idempotency key; retry succeeded once
- Browser: disable and retire, terminal state prevents activation, history retained
- Browser: local approval disclaimer and externalReference visible; Spanish and English UI

Los registros sintéticos quedaron RETIRED, sin versión activa. Proxy de fallo restaurado. La prueba de fallo fue anterior al commit; no demuestra pérdida de respuesta posterior al commit. Simulaciones sin eventos reales ni outbox.

Evidencia: `evidences/auto-suppression/frontend-20260910/report.json` y recibos/historial HTTP.
