# Apariencia compartida con WebUI

Los siete módulos de OEM Dashboards siguen la presentación de Event Management Console: marca EM, sidebar de 240 px, cabecera de 64 px, texto base de 14 px, encabezados de 28 px, tablas compactas y paneles con el mismo espaciado.

`services/oem-dashboards/src/appearance.css` adapta las reglas de `services/event-management-console/src/shared/theme/appearance.css` a los componentes de dashboards. Incluye las mismas variables de color del tema Kyndryl: superficies neutras cálidas, barra superior oscura, acento rojo y botones de alto contraste. El tema Actual mantiene la paleta oscura de WebUI.

`ThemeProvider.tsx` ofrece Actual/Kyndryl, aplica `data-theme` y conserva la preferencia en `console.theme`. Se usan las mismas convenciones de WebUI; el almacenamiento del navegador está aislado por origen, por lo que 8090 y 8091 conservan su selección por separado. El tema predeterminado es Actual, igual que la Console. No se añaden dependencias ni peticiones a fuentes externas.

La adaptación es visual: conserva rutas, contratos, filtros, consultas PostgreSQL/API y controles de servicios. Navegación adaptable, foco visible y soporte de movimiento reducido. Despliegue sobre el Nginx compartido `event-management-console`.

Validación: TypeScript y build Vite aprobados; inspección visual de Delivery y API Management en ambos temas sobre 8091. Selector Kyndryl conservado al recargar. Las 13 APIs continuaron disponibles durante la revisión. Reconstruido únicamente el contenedor compartido de Console para publicar los assets.
