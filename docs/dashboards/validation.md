# Validación — 2026-09-10

Resultado de la foundation 0.1.0:

| Comprobación | Resultado / límite |
|---|---|
| TypeScript + Vite build OEM | PASS |
| TypeScript Console | PASS |
| Contratos TypeScript OEM + regresión administración | PASS, dos archivos Node test |
| Python backend | PASS, 7 pruebas incluyendo PostgreSQL real aislado |
| Migración 018 reejecutable | PASS, aplicada dos veces en PostgreSQL 17 desechable |
| Rol reader | PASS, SELECT sobre vistas; sin SELECT sobre tabla operativa ni DELETE/INSERT |
| Filtros, paginación y búsqueda literal | PASS |
| Paridad PostgreSQL/API | PASS, cuatro dominios; se excluye observedAt de la comparación temporal |
| Source set | PASS: cambio por dominio/global, preflight fallido sin persistir, rollback tras fallo posterior |
| Contrato API, HTML, redirecciones y errores | PASS |
| Compose combinado | PASS, config --quiet |
| Shell CLI | PASS, bash -n + namespace help/source get local |

La API de paridad fue el BFF de prueba consumiendo vistas reales, no un proveedor
externo. PostgreSQL temporal y sus volúmenes se eliminaron al terminar.
No se aplicó 018 a la base operativa ni se recrearon contenedores de plataforma.
No se certificó construcción/arranque de las imágenes nuevas, E2E de navegador,
ingesta, autenticación o despliegue productivo. Esas condiciones no se infieren del build.

Las pruebas se encuentran en `testing/services/oem-dashboards`. Ver runbook para
reproducción. La unidad de permisos verifica privilegios de INSERT explícitamente:
algunas columnas calculadas de las vistas tampoco son insertables por definición,
por lo que un intento de INSERT puede fallar por esa razón antes del chequeo de permisos.

Runner central `dashboards`: PASS. Evidencia local:
`evidences/testing/20260910T072006006492Z-ec616823/dashboards/report.json`.
La primera ejecución del runner quedó FAIL por restricción de sockets del sandbox;
la repetición autorizada con loopback permitido pasó ambas suites. Harness central:
5 pruebas PASS tras añadir las nuevas entradas.

## Despliegue local posterior — 2026-09-10

Desplegado por solicitud del usuario: un solo Nginx/contenedor
`event-management-console`, puertos host 8090/8091. `nginx -t` PASS;
contenedor healthy, Console /health UP, BFF readiness UP.
008/017/018 aplicadas en PostgreSQL operativo; rol lector y configuración
privada provisionados. API pública confirma PostgreSQL para los cuatro módulos.
Demo opt-in separada: 8 eventos, 6 Ticketing, 6 GNM, 7 CACF.
Construcción Docker de las dos SPAs y BFF PASS, consultas HTTP PASS.
La validación anterior describe la etapa previa; el despliegue aquí documentado
sustituye su estado de migración/runtime pendiente. Ingesta sigue fuera de alcance.
Resumen local sin secretos: `.local/oem-dashboards/deployment.json`.

## Delivery e i18n — 2026-09-10

9 pruebas backend PASS en PostgreSQL 17 aislado, incluidos facets DISTINCT,
APPLID/severidad/comodines, restricciones, permisos y paridad con API de cinco módulos.
TypeScript/Vite y contratos de frontend + render en inglés PASS.
Migración 022 y 12 filtros demo aplicados al runtime. Verificación HTTP PASS
con scripts/dashboards/verify-delivery.py: GNM=5, GNM/CORE/severidad5=1,
auditoría=2, severidad comodín=2, APPLID comodín=1. Bundle publicado contiene
Delivery y traducciones inglesas. Console 8090 UP; mismo hosting Nginx.
No se efectuó una certificación E2E de navegador ni una comparación con
resultados de ejecución productiva de legacy.

## Data Collection y API Management — 2026-09-10

- Gateway: 20 pruebas Java aprobadas, incluida respuesta 503 sin aceptación ni UUID cuando falla el almacenamiento original.
- PostgreSQL aislado: 10 pruebas aprobadas; migración 025 idempotente, original byte por byte, hash, protección contra UPDATE/DELETE y separación hoy/histórico.
- Administración: 16 pruebas aprobadas para inventario, comandos permitidos, idempotencia, serialización, fallos y pruebas HTTP sin acciones de negocio.
- TypeScript y compilación Vite aprobados. Navegador: rutas 06/07, datos reales, detalle original y selector español/inglés verificados.
- Despliegue en `event-management-console`, mismo Nginx en 8090/8091. Gateway y BFF usan PostgreSQL real. El catálogo HTTP mostró 13/13 APIs disponibles durante la revisión.
- Se añadió `product-observability` al orden administrado de la CLI y se reutiliza el inventario validado para evitar múltiples consultas Compose redundantes por acción.

Modelos y límites: [Data Collection](data-collection.md), [API Management](api-management.md). El diario conserva sólo originales capturados desde el despliegue; las pruebas históricas utilizan exclusivamente PostgreSQL temporal.

Verificación final HTTP completada a las 15:53 UTC: 13/13 APIs UP; Data Collection PostgreSQL con 29 recepciones del día (25 PUBLISHED y 4 REJECTED), histórico vacío por inicio reciente de captura. Se verificaron testing, stop → UNREACHABLE, start → UP y reboot → succeeded/UP sobre `servicenow-console-mock`. Reinicio confirmado por la operación `00932ff0-e0f8-4934-925e-b559e1ecc002`, exitCode 0. Solicitud de control sin cabeceras requeridas: HTTP 403. Evidencia privada en `.local/oem-dashboards/collection-validation.json`; reproducción de reboot en `scripts/dashboards/verify-api-reboot.py`.

## Ampliación de API Management — 2026-09-10

52 entradas por servicio/capacidad; las 52 verificaciones HTTP mostraron UP al terminar (cada fila indica si se comprobó el endpoint o la salud de su servicio). 18 pruebas del backend aprobadas, TypeScript y Vite aprobados. Navegador: iconos sin texto visible con nombres accesibles, búsqueda y filtro de servicio, prueba de conexión de `gateway-rules` con HTTP 200 y fecha actualizada. Mantiene los temas existentes y el Nginx compartido. Métricas CACF requiere token y usa comprobación de salud, sin confundir HTTP 401 con caída del servicio.

## Subdominio HTTP — 2026-09-10

Corregida la generación de UUID en API Management y los controles/simulaciones de Console: `crypto.randomUUID()` requiere contexto seguro y no está disponible en el subdominio HTTP del laboratorio, aunque funcione en localhost. El helper usa randomUUID cuando existe y genera UUID v4 con `getRandomValues` en HTTP, conservando aleatoriedad criptográfica e idempotencia; no modifica restricciones de origen, CORS ni validaciones del backend.

Prueba de regresión `testing/services/oem-dashboards/uuid.test.mjs`: implementación nativa, ausencia de randomUUID, formato/version/variante UUID y rechazo sin Web Crypto, para ambas aplicaciones. TypeScript y builds de las dos SPAs aprobados. Desplegado en el Nginx compartido. Verificación desde `http://oem-lab.liverpool.com.mx:8091/dashboards/api-management`: 52/52 verificaciones UP y botón Probar conexión del Gateway completado con HTTP 200 y hora actualizada, sin error de UUID.
