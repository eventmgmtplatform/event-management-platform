# Validación local — 2026-09-10

Destino: consola existente `http://localhost:8090/administration`, build Docker React/TypeScript/Vite + Nginx; BFF `frontend-management-api:0.1.0`.

## Resultados

- Build de producción en Docker: PASS.
- TypeScript y pruebas Node de contrato: PASS.
- Cuatro pruebas Python: mapeo de salud (9 escenarios), contenedor ausente / daemon inaccesible, exclusión de secretos y versión de servicio frente a versión de imagen base: PASS.
- Español, fuente API interna, demostración desactivada: 18 componentes; 16 saludables, 1 detenido y 1 tarea finalizada.
- Filtro Detenido: muestra `Enrichment Engine`, diagnóstico `Contenedor detenido`, contenedor `event-enrichment-engine`.
- Inglés: shell, encabezado, filtros, tabla, diagnósticos y detalle traducidos. Filtro Stopped y detalle muestran `Container stopped`. Recargar conserva English.
- Filtro Completed: muestra exclusivamente `Kafka Init` con `Job completed successfully`.
- Detalle: versión corregida `1.0.0` de Enrichment Engine, reinicios 0; cierre por Escape.
- Regreso a español y actualización manual: PASS.
- Proxy local: GET /api/administration/platform devuelve HTTP 200 y datos reales; versiones de gateway/processor 1.0.0, WireMock 3.13.1.
- Healthchecks Compose de consola y BFF: healthy.

Estos conteos son una observación puntual; cambian con el runtime. No se detuvieron servicios Core para provocar fallos: se verificó el componente que ya estaba detenido y se probaron estados unhealthy, missing y fallos de Docker con entradas controladas en las pruebas del BFF. La salud funcional depende de los healthchecks existentes.
