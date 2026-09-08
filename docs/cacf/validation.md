# Validación local CACF

Fecha: 2026-09-08. Alcance: cambios locales sin publicación de release.

## Resultado observado

- Suite completa de integration-worker: **171 pruebas, 0 fallos, 0 errores,
  0 omitidas**, ejecutada con Maven offline y PostgreSQL real del proyecto aislado.
- Siete pruebas JDBC cubren idempotencia, correlación, concurrencia terminal,
  deadline, callbacks tardíos, UNKNOWN y rollback del outbox.
- Imagen Docker del worker construida y ejecutada correctamente.
- Script `scripts/cacf-local-certification.py`: todos los escenarios pasaron.
  Incluye CREATE, ACK, TKTUPDATE, duplicados, reinicio conservando deadline,
  reasignación ServiceNow, timeout sin reversión por RESOLVE tardío, UNKNOWN,
  entrada Kafka, asociación posterior de ticket y rechazo de XML inseguro.
- Reejecución de `008-cacf-core.sql` sobre la base inicializada: COMMIT exitoso;
  las tablas existentes se conservaron.
- `git diff --check`: sin errores.

## Frontera de la evidencia

Las pruebas usan el proyecto Compose `cacf-certification`, PostgreSQL y Kafka
reales, y mocks HTTP locales de NEXT/ServiceNow. Los fixtures XML son sintéticos
derivados del diseño recuperado. No certifican compatibilidad con proveedores
reales ni la proyección final de event-state-service, que no forma parte de este
entorno aislado. No se aplicó la migración al runtime principal ni se modificaron
Cloud Build o Terraform.

Los comandos para reproducir la validación están en [README](README.md).
