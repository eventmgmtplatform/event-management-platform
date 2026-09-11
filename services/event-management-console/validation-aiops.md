# AIOps independiente — validación 2026-09-10

Desplegado en http://localhost:8090/aiops-extensions. CRUD propio mediante `/api/processor/v1/aiops`, paginación de arrays por tenant, ETag e If-Match. POST crea deshabilitado por defecto; PUT habilita directamente la consulta manual. No usa `/rules` ni recibos de idempotencia.

Se verificaron desde navegador creación, reapertura, edición, consulta sintética con revisión, conflicto 409 en segunda pestaña, bloqueo al deshabilitar, baja lógica y rechazo de reutilizar ID. También: tenant separado, páginas de 50 y 1, catálogo de lectura, campos vacíos, severidad entre comillas/fuera de rango, español e inglés. La regresión final confirma baja con cambios no guardados en el formulario.

El fixture local de proxy se limitó a un tenant y un ID. Mostró 503 explícito en consulta y escritura; GET confirmó revisión sin cambio antes de reconciliar manualmente. No se repitió la escritura. El fixture fue retirado; no cambió el mock compartido. No se simuló pérdida de respuesta posterior a un commit.

Evidencia HTTP y reporte: `evidences/aiops/frontend-20260910/`. Capturas emitidas en la tarea, sin PNG exportado al repositorio. Los 52 registros sintéticos fueron dados de baja; lectura final 404 y lista vacía. Tests del cliente/modelo y compilación TypeScript correctos. No se declara certificación backend.

INTERNAL_MOCK es sintético: sin inferencia real ni compatibilidad certificada con Kyndryl Bridge. No remedia, crea tickets ni participa en el pipeline. Señales/resultados no persisten; auditoría de cambios sin endpoint público. DP-EP-01/09 mantienen seguridad, Bridge real, historial e integración al pipeline pendientes.
