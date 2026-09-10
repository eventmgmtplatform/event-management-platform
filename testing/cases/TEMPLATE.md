# UC-NNN — Nombre del caso

- Objetivo y nivel: unidad / contrato / integración / E2E.
- Estado de implementación: planned / blocked / automated / existing.
- Precondiciones: servicios, reglas, datos y comportamiento de proveedores.
- Entrada: fixture, valores variables e identidad del caso.
- Pasos: estímulo → observación → aserción; incluir aserciones negativas.
- Correlación: tenant, eventId/eventKey, processingId, commandId, ticket,
  incidentId y executionId necesarios.
- Tiempo: deadline por paso, polling y criterio de entrega completada.
- Idempotencia: reintentos/replay y efectos que nunca deben duplicarse.
- Limpieza: sólo recursos propios; conducta ante ejecución interrumpida.
- Bloqueos: capacidades faltantes; nunca convertirlos en PASS.
- Ejecución: comando reproducible desde la raíz.
- Evidencias: archivos esperados bajo evidences/testing/<run>/<caso>.
- Cobertura pendiente: variantes y referencias a otros casos.

No pegar resultados, fechas de ejecuciones ni capturas en esta especificación.
