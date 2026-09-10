# Validación Tickets por API — 2026-09-10

Destino: http://localhost:8090/ticketing/tickets

- Build TypeScript/Vite en Docker: PASS.
- Pruebas de mock en SQLite temporal: 12 IDs, seis estados iniciales, filtros, búsqueda/cierre/búsqueda, persistencia tras reinicialización, cierre idempotente y rechazo sin mutación: PASS.
- Pruebas Node del adaptador: validación de respuesta, búsqueda con estado, endpoint same-origin y error sin fallback a seed del navegador: PASS.
- Navegador, español: 12 incidentes iniciales; Abiertos=4, En progreso=3, Pendientes=2, Resueltos=1, Cerrados=1, Fallidos=1.
- Tarjeta En progreso seleccionada: tres resultados; tarjeta Abiertos seleccionada: cuatro.
- Buscar INC0019284: un resultado Abierto; confirmar cierre con código Solved (Permanently) y nota; respuesta Cerrado.
- Recarga completa + buscar INC0019284 + Refresh: permanece Cerrado. El mock queda con 12 incidentes, 3 abiertos y 2 cerrados.
- Inglés: filtro, búsqueda y resultado Closed traducidos.
- Administración: dos fuentes Connected / Conectada, Docker local y servicenow-console-mock; descripción de operaciones y persistencia visible.
- Contenedores actualizados: mock exclusivo, API y consola healthy.
- Aislamiento: `event-servicenow-mock` conserva el ID `24031558beab668cd2d6ce5535d4cbc16888401d973b807644283b96dbd32cf2` y arranque `2026-09-10T04:21:49.576188614Z` antes y después. La comparación de las fixtures copiadas no muestra cambios frente al original.

No se borró o reinicializó la base desplegada después del cierre E2E. INC0019284 queda como evidencia del flujo; los demás incidentes mantienen su estado inicial.
