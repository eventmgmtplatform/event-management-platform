# Releases

## Versionado

Los cambios se identifican por SHA y branch hasta que exista una release
aprobada. No se deben inventar versiones semánticas ni declarar despliegues a
partir de un commit. Los changelogs registran alcance y compatibilidad.

## Proceso

1. Revisar estado del branch, pruebas y `git diff --check`.
2. Actualizar changelogs y documentación del componente.
3. Validar manifiesto, imágenes, migraciones y health checks.
4. Publicar el branch y registrar el SHA real.
5. Crear release/tag sólo con aprobación del proceso de gobierno.

El runbook detallado de publicación está en
[`docs/git/publication-runbook.md`](../git/publication-runbook.md).
