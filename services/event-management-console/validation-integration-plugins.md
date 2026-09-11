# Plugins GNM y CACF — 2026-09-10

Desplegados en http://localhost:8090/notifications y http://localhost:8090/cacf.
Imagen: sha256:c1b622a65986b272cac591f5b91ce65bbd08707e172885713f9a1294d82143d3.
TypeScript y build: PASS. Contenedor healthy.

Navegador: entradas Notifications y CACF visibles bajo Plugins; Notifications lee PostgreSQL (23 registros observados). Búsqueda cliente DEMO-DASHBOARDS y referencia DEMO-GNM-003 devuelve un registro PENDING; detalle correcto. CACF muestra filtro TIMED_OUT seleccionado, un registro y resultado TIMEOUT; detalle correcto. Datos existentes sin escrituras ni nuevos fixtures.

Los adaptadores reutilizan el contrato del dashboard existente; proxy GET exclusivo a gnm/cacf. Errores de fuente/contrato se muestran como errores, sin datos ficticios de respaldo. Son vistas de consulta de notificaciones y automatizaciones conocidas por la plataforma; no hay acciones de proveedor implementadas en este alcance. Traducciones ES/EN y estilos comunes incluidos.
