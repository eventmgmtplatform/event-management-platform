# Desarrollo

## Inicio local

1. Copiar los ejemplos de configuración del servicio que se vaya a ejecutar.
2. Levantar dependencias con los Compose de `infrastructure/`.
3. Ejecutar `scripts/eventmanagement-services.sh` o el comando documentado por
   el servicio.
4. Abrir la consola en `http://localhost:8090` y verificar readiness antes de
   probar mutaciones.

No se deben commitear `.env`, tokens, certificados, dumps, `node_modules`,
`dist` ni evidencias generadas.

## Estructura

- Servicios Java: Maven y `src/main`/`src/test`.
- Frontends React: Vite, `src/` y pruebas bajo `testing/services/`.
- APIs Python: servidor y pruebas junto al servicio.
- Migraciones: `infrastructure/postgres/init`, aditivas y ordenadas.

## Calidad

- Ejecutar el build/test del componente modificado.
- Ejecutar `git diff --check`.
- Añadir o actualizar validación y changelog cuando cambie un contrato.
- Verificar estados de carga, vacío, error, conflicto y tenant en interfaces.
- Mantener las evidencias fuera de Git.

Los estándares de commits, ramas y publicación están en
[`docs/git/`](../git/README.md).
