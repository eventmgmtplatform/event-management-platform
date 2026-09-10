# Consolidación del workspace — 2026-09-10

Solicitud: revisar y publicar los cambios pendientes sin perder código. Rama usada:
`integration/event-management-current-state`; no se promueve main ni se modifica un
checkpoint de release. Los originales y el manifiesto de clasificación se conservan
en `evidences/git-consolidation-20260910/`, fuera de Git.

## Historia y alcance

La rama de integración avanzó por fast-forward desde `fc51edc` hasta `97b46e1`,
conservando los commits ya publicados de Gateway, Processor y ESS. La historia
nominal de blackouts `a1249ba` se incorpora por merge, sin reescribirla.

Los cambios restantes se agrupan en:

1. `5322a73`: lifecycle/recuperación de Processor y Worker; pruebas, fixtures y mocks
   reubicados en testing. Las eliminaciones de rutas anteriores son traslados,
   con ajustes explícitos donde corresponde.
2. `c43690d`: consola, APIs administrativas, dashboards, observabilidad y catálogos.
3. Infraestructura y operación compartidas, scripts y documentación de los trabajos.

Los archivos privados, credenciales reales, compilados y auditoría de ejecución
permanecen excluidos. Las referencias documentales del material externo se conservan
sólo en el respaldo local. Los identificadores de proveedor preexistentes necesarios
para compatibilidad no se cambian. La procedencia de catálogos nuevos usa
`manual`, `legacy` o `demo`; la comprobación local no encontró filas importadas.
No se modifican filas de la base durante esta consolidación Git.

## Validación de publicación

Pruebas Java de los cuatro servicios, contratos de APIs/UI, typecheck de ambas
aplicaciones TypeScript, harness de testing, sintaxis Python/JSON/shell y validación
Docker Compose. La ejecución Java se realiza sobre copias aisladas para no alterar
los targets montados en el runtime.

Los casos que requieren laboratorios no se convierten en PASS si quedan omitidos.
La publicación conserva el estado existente; no equivale a una certificación completa
de producción ni a desplegar nuevamente todos los servicios. Resultados exactos,
SHA y comprobación de limpieza/remotos quedan en el informe de evidencia.
