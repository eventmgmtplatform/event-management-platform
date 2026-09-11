# Frontend inventory-enrichment — 2026-09-10

Desplegado en localhost:8090. Aceptación interactiva en navegador independiente de certificación backend.

- Browser: typed facts boolean and negative decimal without invented range; plan and inventory saved separately
- Browser: required plan without inventory FAILED/DEAD_LETTER; enabling inventory SUCCESS with 4 typed facts and provenance
- Browser: other node FAILED; other tenant NOT_FOUND; facts scoped correctly
- Browser: saved inventory v2 leaves v1 facts until enable; stale second tab HTTP 409
- Browser: competing assignment.group reports selected/rejected provenance conflict
- Browser: disabled inventory required lookup FAILED; optional plan NOT_FOUND/CONTINUE
- Browser: candidateRules includes plan plus inventory, replaces selected same ID and leaves active state unchanged
- Browser: retire terminal and history persists
- Browser: scoped HTTP 503 save, reload retains same key, double-click retry creates one version
- Browser: legacy read-only view; actual catalog empty, explicit labelled fixture used to verify row detail without mutation controls
- Browser: Spanish/English IBM Carbon visual smoke

Los registros sintéticos quedaron RETIRED, sin versión activa. Proxy de fallo restaurado. La prueba de fallo fue anterior al commit; no demuestra pérdida de respuesta posterior al commit. Simulaciones sin eventos reales ni outbox.

Evidencia: `evidences/inventory-enrichment/frontend-20260910/report.json` y recibos/historial HTTP.
