# Dashboard general — 2026-09-10

Desplegado en http://localhost:8090/dashboard. Imagen console 314bd4561d6928e562c2445444cdac522d9c57b1dedecb4524337b5362ba027a; API 850671daff01d02e4c48731e440a6906e0bbcd1bfb26b7d27ed18b3db5648b80.

Inicio conectado a readPlatform, sin estados estáticos ni fallback de demostración. Revisión cada 30 segundos y manual, fecha visible; error elimina snapshot y muestra Sin telemetría. Arquitectura incluye las 24 entradas del catálogo operativo. Añadidos NEXT Mock, Dashboards API y Product Observability, contrastados con los nombres del runtime principal. No se incluyen laboratorios de certificación. Un mismo contenedor soporta Console e ITSM Dashboard: son superficies distintas, no 24 contenedores únicos.

Secciones: tres tarjetas Plugins; Arquitectura; Sistema/Plataforma al 50% en escritorio, una columna en móvil. Todos los recuadros son enlaces. Componentes abren /administration?service=ID directamente en detalle.

TypeScript/build PASS; 4 tests de inventario Python PASS. Navegador PASS: 24 componentes, Enrichment Engine detenido, enlace abre diálogo con diagnóstico Contenedor detenido; cerrar vuelve a administración sin query. Plugin Notifications navega a GNM · Notifications. Captura visual emitida en la tarea. Salud corrobora evidences/dashboard/frontend-20260910/platform.json.

No se arrancó ni detuvo Enrichment Engine: se muestra su estado real. Healthchecks no equivalen a certificación funcional.
