# Estado actual del proyecto

**Corte documental:** 10 de septiembre de 2026<br>
**Rama de referencia:** `feature/os-13-01-webgui-mgmt`<br>
**Commit de referencia:** `fc9ab0e` — `docs: add centralized project documentation baseline`

## Resumen ejecutivo

La plataforma tiene una base integrada para recibir, normalizar, procesar,
enriquecer y consultar eventos operativos. El trabajo más reciente consolidó la
WebGUI administrativa y la documentación central del proyecto. El código local
está sincronizado con `origin` en la rama de trabajo.

## Entregado en el corte más reciente

| Área | Estado | Alcance comprobado en el código |
| --- | --- | --- |
| WebGUI administrativa | Integrado | Shell consolidado, navegación colapsable, ES/EN, persistencia de preferencias y dashboards operativos. |
| Administración del Processor | Integrado | Policies, Routing, Correlation, Inventory/Enrichment, Blackouts, AutoSuppression y AIOps Extensions. |
| Integraciones | Integrado | Ticketing, Notifications, CACF, GLPI, MockSecrets y Middleware/Kafka. |
| ESS | Integrado | Consulta de eventos, búsqueda exacta, detalle/historial y cuarentena global mediante proxy allowlisted. |
| Persistencia y contratos | Integrado | PostgreSQL, ETag/revisión optimista, reintentos idempotentes y contratos same-origin. |
| Validación | En curso continuo | Validaciones de navegador, Node, HTTP y certificaciones del runtime disponibles junto a cada componente. |
| Release productivo | Pendiente de evidencia | La documentación no afirma un despliegue; deben revisarse manifiestos, health checks y evidencias del ambiente objetivo. |

## Temas y experiencia visual

La WebGUI conserva los temas implementados en el producto —Actual, Kyndryl,
IBM Carbon y LIVERPOOL— con preferencia persistente por navegador. Esta wiki
usa exclusivamente la presentación Kyndryl: esquema claro fijo, barra superior
negra, rojo de marca `#ff462d`, tipografía IBM Plex y controles de esquinas
rectas. La wiki no ofrece selector de tema.

## Próximas comprobaciones

- Ejecutar `mkdocs build --strict` antes de cada publicación.
- Verificar los health checks y manifiestos del ambiente destino.
- Mantener los changelogs por componente y separar claramente código integrado,
  validación local y despliegue real.

La fuente de verdad para cambios funcionales sigue siendo el repositorio de
plataforma; esta página resume su estado y no reemplaza las evidencias de
ejecución.
