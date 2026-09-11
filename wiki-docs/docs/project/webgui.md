# WebGUI administrativa

La consola de Event Management es una aplicación React/TypeScript/Vite que
reúne la administración operativa en una sola experiencia. El shell comparte
navegación, encabezado, breadcrumbs, internacionalización ES/EN y estados
explícitos de carga, vacío, error y conflicto.

## Capacidades integradas

- Dashboard y administración de servicios con estado, salud y acciones
  controladas desde el CLI existente.
- Event Management, Correlation, Routing y Policies con lectura, simulación,
  versionado, historial, ETag y protección contra conflictos.
- Inventory/Enrichment, Blackouts, AutoSuppression y AIOps Extensions con
  consultas o escrituras según el contrato de cada módulo.
- ESS, Ticketing, Notifications, CACF, GLPI, Secrets/MockSecrets y
  Middleware/Kafka como herramientas especializadas.
- Cliente e internacionalización persistente en español e inglés.

## Contratos de integración

La consola usa el modelo same-origin y proxies internos cuando necesita acceder
a servicios o mocks. Las operaciones de escritura respetan revisión optimista,
idempotencia y auditoría; los datos muestran su fuente y no se presentan como
desplegados cuando solo han sido validados localmente.

## Identidad visual Kyndryl

El tema Kyndryl de la consola define superficies claras, barra superior negra,
rojo de marca, bordes sobrios y densidad productiva. La wiki adopta el mismo
lenguaje mediante `docs/assets/stylesheets/kyndryl.css`, sin alterar el
contenido existente ni añadir una opción de cambio de tema.

## Referencias del código

- `services/event-management-console/src/layout/ConsoleLayout.tsx`
- `services/event-management-console/src/navigation/navigation.registry.ts`
- `services/event-management-console/src/shared/theme/appearance.css`
- `services/event-management-console/src/shared/theme/ThemeProvider.tsx`
- `services/event-management-console/CHANGELOG.md`
